// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public class Throttler {
  private final long thresholdNs;

  private long lastExecutedAtNs;

  public Throttler(long threshold,
                   @NotNull TimeUnit unit) {
    this.thresholdNs = unit.toNanos(threshold);
  }

  public boolean isTimeForNextRun(long nowNs) {
    if (nowNs - thresholdNs > lastExecutedAtNs) {
      lastExecutedAtNs = nowNs;
      return true;
    }
    return false;
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
