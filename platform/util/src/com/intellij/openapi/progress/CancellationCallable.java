// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import kotlinx.coroutines.CompletableDeferred;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

import static com.intellij.openapi.progress.Cancellation.withJob;

/**
 * A Callable, which, when called, associates the calling thread with a job,
 * invokes original callable, and completes the job with its result.
 *
 * @see CancellationFutureTask
 * @see CancellationRunnable
 */
@Internal
public final class CancellationCallable<V> implements Callable<V> {

  private final @NotNull CompletableDeferred<V> myDeferred;
  private final @NotNull Callable<? extends V> myCallable;

  public CancellationCallable(@NotNull CompletableDeferred<V> deferred, @NotNull Callable<? extends V> callable) {
    myDeferred = deferred;
    myCallable = callable;
  }

  @Override
  public V call() throws Exception {
    try {
      V result = withJob(myDeferred, myCallable::call);
      myDeferred.complete(result);
      return result;
    }
    catch (Throwable e) {
      myDeferred.completeExceptionally(e);
      throw e;
    }
  }
}
