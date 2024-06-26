/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api;

import java.time.Duration;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.utils.internal.RandomUtils.nextLongInclusive;
import static java.lang.Long.numberOfLeadingZeros;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A set of strategies to use for retrying with {@link Publisher#retryWhen(BiIntFunction)},
 * {@link Single#retryWhen(BiIntFunction)}, and {@link Completable#retryWhen(BiIntFunction)} or in general.
 */
public final class RetryStrategies {

    private RetryStrategies() {
        // No instances.
    }

    /**
     * Creates a new retry function that adds the passed constant {@link Duration} as a delay between retries.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter">here</a>.
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     * a failed {@link Completable} with the passed {@link Throwable} as the cause
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param delay Constant {@link Duration} of delay between retries
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithConstantBackoffFullJitter(
            final int maxRetries,
            final Predicate<Throwable> causeFilter,
            final Duration delay,
            final Executor timerExecutor) {
        checkMaxRetries(maxRetries);
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long jitterNanos = delay.toNanos();
        checkFullJitter(jitterNanos);
        return (retryCount, cause) -> retryCount <= maxRetries && causeFilter.test(cause) ?
                timerExecutor.timer(nextLongInclusive(0, jitterNanos), NANOSECONDS) : failed(cause);
    }

    /**
     * Creates a new retry function that adds the passed constant {@link Duration} as a delay between retries.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter">here</a>.
     * a failed {@link Completable} with the passed {@link Throwable} as the cause
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param delay Constant {@link Duration} of delay between retries
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithConstantBackoffFullJitter(
            final Predicate<Throwable> causeFilter,
            final Duration delay,
            final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long jitterNanos = delay.toNanos();
        checkFullJitter(jitterNanos);
        return (retryCount, cause) -> causeFilter.test(cause) ?
                timerExecutor.timer(nextLongInclusive(0, jitterNanos), NANOSECONDS) : failed(cause);
    }

    /**
     * Creates a new retry function that adds the passed constant {@link Duration} as a delay between retries.
     *
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param delay Constant {@link Duration} of delay between retries
     * @param jitter The jitter to apply to {@code delay} on each retry.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithConstantBackoffDeltaJitter(
            final Predicate<Throwable> causeFilter,
            final Duration delay,
            final Duration jitter,
            final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long delayNanos = delay.toNanos();
        final long jitterNanos = jitter.toNanos();
        checkJitterDelta(jitterNanos, delayNanos);
        final long lowerBound = delayNanos - jitterNanos;
        final long upperBound = addWithOverflowProtection(delayNanos, jitterNanos);
        return (retryCount, cause) -> causeFilter.test(cause) ?
                timerExecutor.timer(nextLongInclusive(lowerBound, upperBound), NANOSECONDS) : failed(cause);
    }

    /**
     * Creates a new retry function that adds the passed constant {@link Duration} as a delay between retries.
     *
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     * a failed {@link Completable} with the passed {@link Throwable} as the cause
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param delay Constant {@link Duration} of delay between retries
     * @param jitter The jitter to apply to {@code delay} on each retry.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithConstantBackoffDeltaJitter(
            final int maxRetries,
            final Predicate<Throwable> causeFilter,
            final Duration delay,
            final Duration jitter,
            final Executor timerExecutor) {
        checkMaxRetries(maxRetries);
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long delayNanos = delay.toNanos();
        final long jitterNanos = jitter.toNanos();
        checkJitterDelta(jitterNanos, delayNanos);
        final long lowerBound = delayNanos - jitterNanos;
        final long upperBound = addWithOverflowProtection(delayNanos, jitterNanos);
        return (retryCount, cause) -> retryCount <= maxRetries && causeFilter.test(cause) ?
                timerExecutor.timer(nextLongInclusive(lowerBound, upperBound), NANOSECONDS) : failed(cause);
    }

    /**
     * Creates a new retry function that adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter">here</a>.
     *
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithExponentialBackoffFullJitter(
            final Predicate<Throwable> causeFilter,
            final Duration initialDelay,
            final Duration maxDelay,
            final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long initialDelayNanos = initialDelay.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return (retryCount, cause) -> causeFilter.test(cause) ?
                timerExecutor.timer(nextLongInclusive(0,
                        baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, retryCount)), NANOSECONDS)
                : failed(cause);
    }

    /**
     * Creates a new retry function that adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     * This additionally adds a "Full Jitter" for the backoff as described
     * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter">here</a>.
     *
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     * a failed {@link Completable} with the passed {@link Throwable} as the cause
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithExponentialBackoffFullJitter(
            final int maxRetries,
            final Predicate<Throwable> causeFilter,
            final Duration initialDelay,
            final Duration maxDelay,
            final Executor timerExecutor) {
        checkMaxRetries(maxRetries);
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long initialDelayNanos = initialDelay.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return (retryCount, cause) -> retryCount <= maxRetries && causeFilter.test(cause) ?
                timerExecutor.timer(nextLongInclusive(0,
                        baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, retryCount)), NANOSECONDS)
                : failed(cause);
    }

    /**
     * Creates a new retry function that adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     *
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param jitter The jitter to apply to {@code delay} on each retry.
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithExponentialBackoffDeltaJitter(
            final Predicate<Throwable> causeFilter,
            final Duration initialDelay,
            final Duration jitter,
            final Duration maxDelay,
            final Executor timerExecutor) {
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long initialDelayNanos = initialDelay.toNanos();
        final long jitterNanos = jitter.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return (retryCount, cause) -> {
            if (!causeFilter.test(cause)) {
                return failed(cause);
            }
            final long baseDelayNanos = baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, retryCount);
            final long lowerBound = max(0, baseDelayNanos - jitterNanos);
            final long upperBound = min(maxDelayNanos, addWithOverflowProtection(baseDelayNanos, jitterNanos));
            return timerExecutor.timer(nextLongInclusive(lowerBound, upperBound), NANOSECONDS);
        };
    }

    /**
     * Creates a new retry function that adds a delay between retries. For first retry, the delay is
     * {@code initialDelay} which is increased exponentially for subsequent retries.
     *
     * @param maxRetries Maximum number of allowed retries, after which the returned {@link BiIntFunction} will return
     * a failed {@link Completable} with the passed {@link Throwable} as the cause
     * @param causeFilter A {@link Predicate} that selects whether a {@link Throwable} cause should be retried
     * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry
     * @param jitter The jitter to apply to {@code delay} on each retry.
     * @param maxDelay The maximum amount of delay that will be introduced.
     * @param timerExecutor {@link Executor} to be used to schedule timers for backoff
     * @return A {@link BiIntFunction} to be used for retries which given a retry count and a {@link Throwable} returns
     * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
     * if the source should not be retried for the passed {@link Throwable}
     */
    public static BiIntFunction<Throwable, Completable> retryWithExponentialBackoffDeltaJitter(
            final int maxRetries,
            final Predicate<Throwable> causeFilter,
            final Duration initialDelay,
            final Duration jitter,
            final Duration maxDelay,
            final Executor timerExecutor) {
        checkMaxRetries(maxRetries);
        requireNonNull(timerExecutor);
        requireNonNull(causeFilter);
        final long initialDelayNanos = initialDelay.toNanos();
        final long jitterNanos = jitter.toNanos();
        final long maxDelayNanos = maxDelay.toNanos();
        final long maxInitialShift = maxShift(initialDelayNanos);
        return (retryCount, cause) -> {
            if (retryCount > maxRetries || !causeFilter.test(cause)) {
                return failed(cause);
            }
            final long baseDelayNanos = baseDelayNanos(initialDelayNanos, maxDelayNanos, maxInitialShift, retryCount);
            return timerExecutor.timer(
                    nextLongInclusive(max(0, baseDelayNanos - jitterNanos),
                                    min(maxDelayNanos, addWithOverflowProtection(baseDelayNanos, jitterNanos))),
                    NANOSECONDS);
        };
    }

    static long baseDelayNanos(final long initialDelayNanos, final long maxDelayNanos, final long maxInitialShift,
                               final int count) {
        return min(maxDelayNanos, initialDelayNanos << min(maxInitialShift, count - 1));
    }

    static void checkMaxRetries(final int maxRetries) {
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries: " + maxRetries + " (expected: >0)");
        }
    }

    static void checkJitterDelta(long jitterNanos, long delayNanos) {
        if (jitterNanos >= delayNanos || Long.MAX_VALUE - delayNanos < jitterNanos) {
            throw new IllegalArgumentException("jitter " + jitterNanos +
                    "ns would result in [under|over]flow as a delta to delay " + delayNanos + "ns");
        }
    }

    static void checkFullJitter(long jitterNanos) {
        if (jitterNanos <= 0) {
            throw new IllegalArgumentException("jitter " + jitterNanos + "ns must be >=0.");
        }
    }

    static long maxShift(long v) {
        return numberOfLeadingZeros(v) - 1;
    }
}
