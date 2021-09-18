// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.AccessToken;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.JobKt;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

import static com.intellij.openapi.progress.Cancellation.currentJob;
import static com.intellij.openapi.progress.Cancellation.withJob;
import static kotlinx.coroutines.CompletableDeferredKt.CompletableDeferred;

public final class JobFutureTask<V> extends FutureTask<V> {

  private JobFutureTask(@NotNull Callable<V> callable) {
    super(callable);
  }

  public static <V> @NotNull RunnableFuture<V> jobRunnableFuture(@NotNull Callable<? extends V> callable) {
    return new JobFutureTask<>(jobCallable(callable));
  }

  /**
   * Creates a Callable instance, which, when called, associates the calling thread with a job.
   * This job becomes a child of the job, which is currently associated with this thread, or root if there is no current Job.
   */
  private static <V> @NotNull Callable<V> jobCallable(@NotNull Callable<? extends V> callable) {
    CompletableDeferred<V> deferred = CompletableDeferred(currentJob());
    return () -> {
      try {
        V result;
        try (AccessToken ignored = withJob(deferred)) {
          result = callable.call();
        }
        deferred.complete(result);
        return result;
      }
      catch (Throwable e) {
        deferred.completeExceptionally(e);
        throw e;
      }
    };
  }

  /**
   * @see #jobCallable(Callable)
   */
  public static @NotNull Runnable jobRunnable(@NotNull Runnable runnable) {
    if (runnable instanceof JobFutureTask) {
      return runnable;
    }
    CompletableJob job = JobKt.Job(currentJob());
    return () -> {
      try {
        try (AccessToken ignored = withJob(job)) {
          runnable.run();
        }
        job.complete();
      }
      catch (Throwable e) {
        job.completeExceptionally(e);
        throw e;
      }
    };
  }
}
