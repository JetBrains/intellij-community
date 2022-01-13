// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.AccessToken;
import kotlinx.coroutines.CompletableDeferred;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

import static com.intellij.openapi.progress.Cancellation.currentJob;
import static com.intellij.openapi.progress.Cancellation.withJob;
import static kotlinx.coroutines.CompletableDeferredKt.CompletableDeferred;

@Internal
public final class JobFutureTask<V> extends FutureTask<V> {

  private final @NotNull CompletableDeferred<V> myJob;

  private JobFutureTask(@NotNull CompletableDeferred<V> job, @NotNull Callable<V> callable) {
    super(jobCallable(job, callable));
    myJob = job;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    myJob.cancel(null);
    return super.cancel(mayInterruptIfRunning);
  }

  /**
   * Creates a RunnableFuture instance with a job.
   * <ul>
   * <li>The job becomes a child of the current thread job, or a root job if there is no current job.</li>
   * <li>The job becomes the current job inside the callable.</li>
   * <li>The returned Future cancels its job when it's cancelled.</li>
   * </ul>
   */
  public static <V> @NotNull RunnableFuture<V> jobRunnableFuture(@NotNull Callable<V> callable) {
    return new JobFutureTask<>(CompletableDeferred(currentJob()), callable);
  }

  /**
   * Creates a Callable instance, which, when called, associates the calling thread with a job,
   * invokes original callable, and completes the job its result.
   */
  private static @NotNull <V> Callable<V> jobCallable(@NotNull CompletableDeferred<V> deferred, @NotNull Callable<? extends V> callable) {
    return () -> {
      try (AccessToken ignored = withJob(deferred)) {
        V result = callable.call();
        deferred.complete(result);
        return result;
      }
      catch (JobCanceledException e) {
        throw deferred.getCancellationException();
      }
      catch (Throwable e) {
        deferred.completeExceptionally(e);
        throw e;
      }
    };
  }
}
