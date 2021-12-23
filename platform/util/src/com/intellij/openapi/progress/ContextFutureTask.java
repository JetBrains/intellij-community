// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ThreadContext;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CompletableDeferred;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

import static com.intellij.openapi.progress.Cancellation.withJob;
import static kotlinx.coroutines.CompletableDeferredKt.CompletableDeferred;

@Internal
public final class ContextFutureTask<V> extends FutureTask<V> {

  private final @NotNull CompletableDeferred<V> myJob;

  private ContextFutureTask(@NotNull CompletableDeferred<V> job, @NotNull Callable<V> callable) {
    super(callable);
    myJob = job;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    myJob.cancel(null);
    return super.cancel(mayInterruptIfRunning);
  }

  /**
   * Creates a RunnableFuture instance with a context.
   * <ul>
   * <li>The context job becomes a child of the current thread job, or a root job if there is no current job.</li>
   * <li>The context job becomes the current job inside the callable.</li>
   * <li>The returned Future cancels its context job when it's cancelled.</li>
   * </ul>
   */
  public static <V> @NotNull RunnableFuture<V> contextRunnableFuture(@NotNull Callable<? extends V> callable) {
    CoroutineContext currentThreadContext = ThreadContext.currentThreadContext();
    CompletableDeferred<V> deferred = CompletableDeferred(Cancellation.contextJob(currentThreadContext));
    return new ContextFutureTask<>(deferred, contextCallable(currentThreadContext, deferred, callable));
  }

  /**
   * Creates a Callable instance, which, when called, associates the calling thread with a job,
   * invokes original callable, and completes the job its result.
   */
  private static @NotNull <V> Callable<V> contextCallable(
    @NotNull CoroutineContext parentContext,
    @NotNull CompletableDeferred<V> deferred,
    @NotNull Callable<? extends V> callable
  ) {
    return () -> {
      ThreadContext.checkUninitializedThreadContext();
      try (AccessToken ignored = ThreadContext.resetThreadContext(parentContext)) {
        V result = withJob(deferred, callable::call);
        deferred.complete(result);
        return result;
      }
      catch (Throwable e) {
        deferred.completeExceptionally(e);
        throw e;
      }
    };
  }
}
