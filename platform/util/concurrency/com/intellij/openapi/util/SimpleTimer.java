// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simple timer that keeps order of scheduled tasks
 */
public final class SimpleTimer {
  private static final SimpleTimer ourInstance = newInstance("Shared");

  // restrict threads running tasks to one since same-delay-tasks must be executed sequentially
  private final ScheduledExecutorService myScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService(
    "SimpleTimer Pool", 1);
  @NotNull private final String myName;

  private SimpleTimer(@NotNull String name) {
    myName = name;
  }

  public static SimpleTimer getInstance() {
    return ourInstance;
  }

  public static SimpleTimer newInstance(@NotNull String name) {
    return new SimpleTimer(name);
  }

  @NotNull
  public SimpleTimerTask setUp(@NotNull final Runnable runnable, final long delay) {
    final ScheduledFuture<?> future = myScheduledExecutorService.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    return new SimpleTimerTask() {
      @Override
      public void cancel() {
        future.cancel(false);
      }

      @Override
      public boolean isCancelled() {
        return future.isCancelled();
      }
    };
  }

  @Override
  public String toString() {
    return "SimpleTimer "+myName;
  }
}