// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
public final class TimeoutUtil {
  /**
   * @deprecated consider another approach to execute runnable on a background thread
   * @see com.intellij.openapi.application.Application#executeOnPooledThread(java.util.concurrent.Callable)
   * @see com.intellij.openapi.progress.util.ProgressIndicatorUtils#withTimeout(long, com.intellij.openapi.util.Computable)
   * @see com.intellij.util.concurrency.AppExecutorUtil#getAppScheduledExecutorService()
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static void executeWithTimeout(long timeout, long sleep, @NotNull final Runnable run) {
    final long start = System.currentTimeMillis();
    final AtomicBoolean done = new AtomicBoolean(false);
    final Thread thread = new Thread("Fast Function Thread@" + run.hashCode()) {
      @Override
      public void run() {
        run.run();
        done.set(true);
      }
    };
    thread.start();

    while (!done.get() && System.currentTimeMillis() - start < timeout) {
      try {
        //noinspection BusyWait
        Thread.sleep(sleep);
      } catch (InterruptedException e) {
        break;
      }
    }
    if (!thread.isInterrupted()) {
      thread.stop();
    }
  }

  /**
   * @deprecated consider another approach to execute runnable on a background thread
   * @see com.intellij.openapi.application.Application#executeOnPooledThread(java.util.concurrent.Callable)
   * @see com.intellij.openapi.progress.util.ProgressIndicatorUtils#withTimeout(long, com.intellij.openapi.util.Computable)
   * @see com.intellij.util.concurrency.AppExecutorUtil#getAppScheduledExecutorService()
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static void executeWithTimeout(long timeout, @NotNull final Runnable run) {
    executeWithTimeout(timeout, 50, run);
  }

  public static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ignored) { }
  }

  public static long getDurationMillis(long startNanoTime) {
    return (System.nanoTime() - startNanoTime) / 1000000;
  }

  /**
   * @return time of running {@code runnable} in milliseconds
   */
  @ApiStatus.Experimental
  public static <E extends Throwable> long measureExecutionTime(@NotNull ThrowableRunnable<E> runnable) throws E {
    long startTime = System.nanoTime();
    runnable.run();
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
  }
}
