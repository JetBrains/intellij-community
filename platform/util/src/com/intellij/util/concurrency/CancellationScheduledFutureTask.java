// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

final class CancellationScheduledFutureTask<V> extends SchedulingWrapper.MyScheduledFutureTask<V> {

  private final @NotNull Job myJob;

  CancellationScheduledFutureTask(@NotNull SchedulingWrapper self, @NotNull Job job, @NotNull Callable<V> callable, long ns) {
    self.super(callable, ns);
    myJob = job;
  }

  CancellationScheduledFutureTask(@NotNull SchedulingWrapper self, @NotNull Job job, @NotNull Runnable r, long ns, long period) {
    self.super(r, null, ns, period);
    myJob = job;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = super.cancel(mayInterruptIfRunning);
    myJob.cancel(null);
    return result;
  }
}
