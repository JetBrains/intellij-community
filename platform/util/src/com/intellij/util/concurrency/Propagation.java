// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ContextCallable;
import com.intellij.concurrency.ContextRunnable;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.CancellationCallable;
import com.intellij.openapi.progress.CancellationFutureTask;
import com.intellij.openapi.progress.CancellationRunnable;
import com.intellij.openapi.util.registry.Registry;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static kotlinx.coroutines.CompletableDeferredKt.CompletableDeferred;
import static kotlinx.coroutines.JobKt.Job;

final class Propagation {

  private Propagation() { }

  private static boolean propagateThreadContext() {
    return Registry.is("ide.propagate.context");
  }

  private static boolean propagateCancellation() {
    return Registry.is("ide.propagate.cancellation");
  }

  static @NotNull Runnable handleCommand(@NotNull Runnable command) {
    if (propagateCancellation()) {
      //noinspection TestOnlyProblems
      Job currentJob = Cancellation.currentJob();
      CompletableJob childJob = Job(currentJob);
      return handleContext(new CancellationRunnable(childJob, command));
    }
    else {
      return handleContext(command);
    }
  }

  static <V> @NotNull FutureTask<V> handleTask(@NotNull Callable<V> callable) {
    if (propagateCancellation()) {
      //noinspection TestOnlyProblems
      Job currentJob = Cancellation.currentJob();
      CompletableDeferred<V> childDeferred = CompletableDeferred(currentJob);
      CancellationCallable<V> cancellationCallable = new CancellationCallable<>(childDeferred, callable);
      return new CancellationFutureTask<>(childDeferred, handleContext(cancellationCallable));
    }
    else {
      return new FutureTask<>(handleContext(callable));
    }
  }

  private static @NotNull Runnable handleContext(@NotNull Runnable runnable) {
    if (propagateThreadContext()) {
      return new ContextRunnable(runnable);
    }
    else {
      return runnable;
    }
  }

  private static <V> @NotNull Callable<V> handleContext(@NotNull Callable<V> callable) {
    if (propagateThreadContext()) {
      return new ContextCallable<>(callable);
    }
    else {
      return callable;
    }
  }
}
