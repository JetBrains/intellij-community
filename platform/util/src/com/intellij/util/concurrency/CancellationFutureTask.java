// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * A FutureTask, which cancels the given job when it's cancelled.
 */
final class CancellationFutureTask<V> extends FutureTask<V> {

  private final @NotNull Job myJob;

  CancellationFutureTask(@NotNull Job job, @NotNull Callable<V> callable) {
    super(callable);
    myJob = job;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = super.cancel(mayInterruptIfRunning);
    myJob.cancel(null);
    return result;
  }
}
