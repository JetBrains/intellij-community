// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@ApiStatus.Internal
public class ThreadSafeThrottler {
  private final long thresholdNs;

  private final AtomicLong lastExecutedAtNs = new AtomicLong();

  public ThreadSafeThrottler(long threshold,
                             @NotNull TimeUnit unit) {
    this.thresholdNs = unit.toNanos(threshold);
  }

  public boolean isTimeForNextRun(long nowNs) {
    while(true) {//CAS-loop:
      long lastExecuted = lastExecutedAtNs.get();
      if (nowNs - thresholdNs <= lastExecuted) {
        return false;
      }
      if(lastExecutedAtNs.compareAndSet(lastExecuted, nowNs)) {
        return true;
      }
    }
  }

  public <E extends Throwable> boolean runThrottled(long nowNs,
                                                    @NotNull ThrowableRunnable<E> task) throws E {
    if (isTimeForNextRun(nowNs)) {
      task.run();
      return true;
    }
    return false;
  }
}
