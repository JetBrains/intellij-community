// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ContextCallable;
import com.intellij.concurrency.ContextRunnable;
import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.SchedulingWrapper.MyScheduledFutureTask;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static kotlinx.coroutines.CompletableDeferredKt.CompletableDeferred;
import static kotlinx.coroutines.JobKt.Job;

@Internal
public final class Propagation {

  private static class Holder {
    private static boolean propagateThreadContext = Registry.is("ide.propagate.context");
    private static boolean propagateThreadCancellation = Registry.is("ide.propagate.cancellation");
  }

  @TestOnly
  static void runWithContextPropagationEnabled(@NotNull Runnable runnable) {
    boolean propagateThreadContext = Holder.propagateThreadContext;
    Holder.propagateThreadContext = true;
    try {
      runnable.run();
    }
    finally {
      Holder.propagateThreadContext = propagateThreadContext;
    }
  }

  @TestOnly
  static void runWithCancellationPropagationEnabled(@NotNull Runnable runnable) {
    boolean propagateThreadCancellation = Holder.propagateThreadCancellation;
    Holder.propagateThreadCancellation = true;
    try {
      runnable.run();
    }
    finally {
      Holder.propagateThreadCancellation = propagateThreadCancellation;
    }
  }

  private Propagation() { }

  static boolean isPropagateThreadContext() {
    return Holder.propagateThreadContext;
  }

  static boolean isPropagateCancellation() {
    return Holder.propagateThreadCancellation;
  }

  static @NotNull Runnable capturePropagationAndCancellationContext(@NotNull Runnable command) {
    if (isPropagateCancellation()) {
      //noinspection TestOnlyProblems
      Job currentJob = Cancellation.currentJob();
      CompletableJob childJob = Job(currentJob);
      command = new CancellationRunnable(childJob, command);
    }
    return capturePropagationContext(command);
  }

  public static @NotNull Pair<Runnable, Condition<?>> capturePropagationAndCancellationContext(
    @NotNull Runnable command,
    @NotNull Condition<?> expired
  ) {
    if (isPropagateCancellation()) {
      //noinspection TestOnlyProblems
      Job currentJob = Cancellation.currentJob();
      CompletableJob childJob = Job(currentJob);
      expired = cancelIfExpired(expired, childJob);
      command = new CancellationRunnable(childJob, command);
    }
    return Pair.create(capturePropagationContext(command), expired);
  }

  private static <T> @NotNull Condition<T> cancelIfExpired(@NotNull Condition<? super T> expiredCondition, @NotNull Job childJob) {
    return t -> {
      boolean expired = expiredCondition.value(t);
      if (expired) {
        // Cancel to avoid a hanging child job which will prevent completion of the parent one.
        childJob.cancel(null);
        return true;
      }
      else {
        // Treat runnable as expired if its job was already cancelled.
        return childJob.isCancelled();
      }
    };
  }

  private static @NotNull Runnable capturePropagationContext(@NotNull Runnable runnable) {
    if (isPropagateThreadContext()) {
      return ThreadContext.captureThreadContext(runnable);
    }
    return runnable;
  }

  static <V> @NotNull FutureTask<V> capturePropagationAndCancellationContext(@NotNull Callable<V> callable) {
    if (isPropagateCancellation()) {
      //noinspection TestOnlyProblems
      Job currentJob = Cancellation.currentJob();
      CompletableDeferred<V> childDeferred = CompletableDeferred(currentJob);
      CancellationCallable<V> cancellationCallable = new CancellationCallable<>(childDeferred, callable);
      return new CancellationFutureTask<>(childDeferred, capturePropagationContext(cancellationCallable));
    }
    return new FutureTask<>(capturePropagationContext(callable));
  }

  static <V> @NotNull MyScheduledFutureTask<V> capturePropagationAndCancellationContext(
    @NotNull SchedulingWrapper wrapper,
    @NotNull Callable<V> callable,
    long ns
  ) {
    if (isPropagateCancellation()) {
      //noinspection TestOnlyProblems
      Job currentJob = Cancellation.currentJob();
      CompletableDeferred<V> childDeferred = CompletableDeferred(currentJob);
      CancellationCallable<V> cancellationCallable = new CancellationCallable<>(childDeferred, callable);
      return new CancellationScheduledFutureTask<>(wrapper, childDeferred, capturePropagationContext(cancellationCallable), ns);
    }
    return wrapper.new MyScheduledFutureTask<>(capturePropagationContext(callable), ns);
  }

  private static <V> @NotNull Callable<V> capturePropagationContext(@NotNull Callable<V> callable) {
    if (isPropagateThreadContext()) {
      return new ContextCallable<>(false, callable);
    }
    return callable;
  }

  static @NotNull MyScheduledFutureTask<?> capturePropagationAndCancellationContext(
    @NotNull SchedulingWrapper wrapper,
    @NotNull Runnable runnable,
    long ns,
    long period
  ) {
    if (isPropagateCancellation()) {
      //noinspection TestOnlyProblems
      Job currentJob = Cancellation.currentJob();
      CompletableJob childJob = Job(currentJob);
      PeriodicCancellationRunnable cancellationRunnable = new PeriodicCancellationRunnable(childJob, runnable);
      return new CancellationScheduledFutureTask<>(wrapper, childJob, wrapWithPropagationContext(cancellationRunnable), ns, period);
    }
    return wrapper.new MyScheduledFutureTask<Void>(wrapWithPropagationContext(runnable), null, ns, period);
  }

  private static @NotNull Runnable wrapWithPropagationContext(@NotNull Runnable runnable) {
    if (isPropagateThreadContext()) {
      return new ContextRunnable(false, runnable);
    }
    return runnable;
  }

  private static final class CancellationScheduledFutureTask<V> extends SchedulingWrapper.MyScheduledFutureTask<V> {
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
}
