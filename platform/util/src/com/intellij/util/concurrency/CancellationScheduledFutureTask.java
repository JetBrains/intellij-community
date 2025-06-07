// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

final class CancellationScheduledFutureTask<V> extends SchedulingWrapper.MyScheduledFutureTask<V> {

  private final @Nullable Job myJob;
  private final @NotNull ChildContext myChildContext;
  private final @NotNull AtomicBoolean myExecutionTracker;

  CancellationScheduledFutureTask(@NotNull SchedulingWrapper self,
                                  @NotNull ChildContext context,
                                  @Nullable Job job,
                                  @NotNull AtomicBoolean executionTracker,
                                  @NotNull Callable<V> callable,
                                  long ns) {
    self.super(callable, ns);
    myJob = job;
    myChildContext = context;
    myExecutionTracker = executionTracker;
  }

  CancellationScheduledFutureTask(@NotNull SchedulingWrapper self,
                                  @NotNull ChildContext context,
                                  @Nullable Job job,
                                  @NotNull Runnable r,
                                  long ns,
                                  long period) {
    self.super(r, null, ns, period);
    myJob = job;
    myChildContext = context;
    myExecutionTracker = new AtomicBoolean(false);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = super.cancel(mayInterruptIfRunning);
    if (myJob != null) {
      myJob.cancel(null);
    }
    if (!myExecutionTracker.getAndSet(true)) {
      myChildContext.cancelAllIntelliJElements();
    }
    return result;
  }
}
