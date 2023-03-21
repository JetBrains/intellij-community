// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import kotlinx.coroutines.CompletableJob;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * A Callable, which, when called, associates the calling thread with a job,
 * invokes original callable, and completes the job with its result.
 *
 * @see CancellationFutureTask
 * @see CancellationRunnable
 */
final class CancellationCallable<V> implements Callable<V> {

  private final @NotNull CompletableJob myJob;
  private final @NotNull Callable<? extends V> myCallable;

  CancellationCallable(@NotNull CompletableJob job, @NotNull Callable<? extends V> callable) {
    myJob = job;
    myCallable = callable;
  }

  @Override
  public V call() throws Exception {
    try {
      V result = myCallable.call();
      myJob.complete();
      return result;
    }
    catch (Throwable e) {
      myJob.completeExceptionally(e);
      throw e;
    }
  }
}
