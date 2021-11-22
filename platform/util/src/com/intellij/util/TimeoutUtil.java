// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/**
 * @author Konstantin Bulenkov
 */
public final class TimeoutUtil {

  public static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ignored) { }
  }

  public static long getDurationMillis(long startNanoTime) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanoTime);
  }

  /**
   * @return time of running {@code runnable} in milliseconds
   * @see #compute(ThrowableComputable, long, LongConsumer)
   * @see #run(ThrowableRunnable, long, LongConsumer)
   */
  @ApiStatus.Experimental
  public static <E extends Throwable> long measureExecutionTime(@NotNull ThrowableRunnable<E> runnable) throws E {
    long startTime = System.nanoTime();
    runnable.run();
    return getDurationMillis(startTime);
  }

  /**
   * @param runnable a task which execution time is needed to measure
   * @param consumer a consumer of a task execution time in milliseconds
   */
  @ApiStatus.Experimental
  public static <E extends Throwable>
  void run(@NotNull ThrowableRunnable<E> runnable, @NotNull LongConsumer consumer) throws E {
    run(runnable, Long.MIN_VALUE, consumer);
  }

  /**
   * @param runnable  a task which execution time is needed to measure
   * @param threshold a minimal value from which duration will be passed to consumer
   * @param consumer  a consumer of a task execution time in milliseconds
   */
  @ApiStatus.Experimental
  public static <E extends Throwable>
  void run(@NotNull ThrowableRunnable<E> runnable, long threshold, @NotNull LongConsumer consumer) throws E {
    long startNanoTime = System.nanoTime();
    try {
      runnable.run();
    }
    finally {
      long duration = getDurationMillis(startNanoTime);
      if (duration >= threshold) consumer.accept(duration);
    }
  }

  /**
   * @param computable a task which computation time is needed to measure
   * @param consumer   a consumer of a task computation time in milliseconds
   * @return a result of computation
   */
  @ApiStatus.Experimental
  public static <T, E extends Throwable>
  T compute(@NotNull ThrowableComputable<T, E> computable, @NotNull LongConsumer consumer) throws E {
    return compute(computable, Long.MIN_VALUE, consumer);
  }

  /**
   * @param computable a task which computation time is needed to measure
   * @param threshold  a minimal value from which duration will be passed to consumer
   * @param consumer   a consumer of a task computation time in milliseconds
   * @return a result of computation
   */
  @ApiStatus.Experimental
  public static <T, E extends Throwable>
  T compute(@NotNull ThrowableComputable<T, E> computable, long threshold, @NotNull LongConsumer consumer) throws E {
    long startNanoTime = System.nanoTime();
    try {
      return computable.compute();
    }
    finally {
      long duration = getDurationMillis(startNanoTime);
      if (duration >= threshold) consumer.accept(duration);
    }
  }
}
