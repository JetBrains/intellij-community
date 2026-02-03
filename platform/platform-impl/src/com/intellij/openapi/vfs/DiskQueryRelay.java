// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.intellij.openapi.progress.ContextKt.isInCancellableContext;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A utility to run a potentially long function on a pooled thread, wait for it in an interruptible way,
 * and reuse that computation if it's needed again if it's still running.
 * Function results should be ready for concurrent access, preferably thread-safe.
 * <p>
 * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
 * inside the {@code function} block.
 * <p>
 * An instance of the class should be used for performing multiple similar operations;
 * for one-shot tasks, {@link #compute(ThrowableComputable)} is simpler to use.
 */
@ApiStatus.Internal
public final class DiskQueryRelay<Param, Result> {
  private final Function<? super Param, ? extends Result> myFunction;

  /**
   * We remember the submitted tasks in "myTasks" until they're finished, to avoid creating many-many similar threads
   * in case the callee is interrupted by "checkCanceled", restarted, comes again with the same query, is interrupted again, and so on.
   */
  private final Map<Param, Future<Result>> myTasks = new ConcurrentHashMap<>();
  private final ExecutorService executor;

  public DiskQueryRelay(@NotNull Function<? super Param, ? extends Result> function) {
    this(function, ProcessIOExecutorService.INSTANCE);
  }

  public DiskQueryRelay(@NotNull Function<? super Param, ? extends Result> function, @NotNull ExecutorService executor) {
    myFunction = arg -> {
      long startedAtNs = System.nanoTime();
      try {
        return function.apply(arg);
      }
      finally {
        long elapsedNs = System.nanoTime() - startedAtNs;
        taskExecutionTotalTimeNs.addAndGet(elapsedNs);
        tasksExecutedCount.incrementAndGet();
      }
    };
    this.executor = executor;
  }

  public Result accessDiskWithCheckCanceled(@NotNull Param arg) {
    long startedAtNs = System.nanoTime();
    try {
      if (!isInCancellableContext()) {
        return myFunction.apply(arg);
      }
      Future<Result> future = myTasks.computeIfAbsent(arg, eachArg -> executor.submit(() -> {
        try {
          return myFunction.apply(eachArg);
        }
        finally {
          myTasks.remove(eachArg);
        }
      }));
      if (future.isDone()) {
        // maybe it was very fast and completed before being put into a map
        myTasks.remove(arg, future);
      }
      return ProgressIndicatorUtils.awaitWithCheckCanceled(future);
    }
    finally {
      long elapsedNs = System.nanoTime() - startedAtNs;
      taskWaitingTotalTimeNs.addAndGet(elapsedNs);
      tasksRequestedCount.incrementAndGet();
    }
  }

  /**
   * Use the method for one-shot tasks; for performing multiple similar operations, prefer an instance of the class.
   * <p>
   * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
   * inside the {@code task} block.
   */
  public static <Result, E extends Exception> Result compute(@NotNull ThrowableComputable<Result, E> task)
    throws E, ProcessCanceledException {
    return compute(task, ProcessIOExecutorService.INSTANCE);
  }

  /**
   * Use the method for one-shot tasks; for performing multiple similar operations, prefer an instance of the class.
   * <p>
   * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
   * inside the {@code task} block.
   */
  public static <Result, E extends Exception> Result compute(@NotNull ThrowableComputable<Result, E> task,
                                                             @NotNull ExecutorService executor) throws E, ProcessCanceledException {
    if (!isInCancellableContext()) {
      return task.compute();
    }

    Future<Result> future = executor.submit(task::compute);
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(future);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (RuntimeException wrapper) {
      Throwable outerCause = wrapper.getCause();
      if (outerCause instanceof ExecutionException) {
        Throwable t = outerCause.getCause();
        ExceptionUtil.rethrowUnchecked(t);
        @SuppressWarnings("unchecked") E innerCause = (E)t;
        throw innerCause;
      }
      else {
        throw wrapper;
      }
    }
    finally {
      //Better .cancel(true) here, but thread interruption is too intrusive, so it is cheaper
      // to allow the task to uselessly finish than to safely deal with thread interruption
      // everywhere (see IDEA-319309)
      future.cancel(false);
    }
  }


  // ==================================== monitoring: ====================================================== //

  /** total time (since app start) of actual task executions, ns */
  private static final AtomicLong taskExecutionTotalTimeNs = new AtomicLong();
  /** total time (since app start) spent waiting for the task result, ns */
  private static final AtomicLong taskWaitingTotalTimeNs = new AtomicLong();
  /** total (since app start) number of tasks actually executed */
  private static final AtomicInteger tasksExecutedCount = new AtomicInteger();
  /** total (since app start) number of tasks requested. Could be <= tasksExecuted because of task coalescing */
  private static final AtomicInteger tasksRequestedCount = new AtomicInteger();

  public static long taskExecutionTotalTime(@NotNull TimeUnit unit) {
    return unit.convert(taskExecutionTotalTimeNs.get(), NANOSECONDS);
  }

  public static long taskWaitingTotalTime(@NotNull TimeUnit unit) {
    return unit.convert(taskWaitingTotalTimeNs.get(), NANOSECONDS);
  }

  public static int tasksExecuted() {
    return tasksExecutedCount.get();
  }

  public static int tasksRequested() {
    return tasksRequestedCount.get();
  }

  static {
    var otelMeter = TelemetryManager.getInstance().getMeter(PlatformScopesKt.PlatformMetrics);

    var taskExecutionTimeUs = otelMeter.counterBuilder("DiskQueryRelay.taskExecutionTotalTimeUs").buildObserver();
    var taskWaitingTimeUs = otelMeter.counterBuilder("DiskQueryRelay.taskWaitingTotalTimeUs").buildObserver();
    var tasksExecuted = otelMeter.counterBuilder("DiskQueryRelay.tasksExecuted").buildObserver();
    var tasksRequested = otelMeter.counterBuilder("DiskQueryRelay.tasksRequested").buildObserver();

    //noinspection resource
    otelMeter.batchCallback(
      () -> {
        taskExecutionTimeUs.record(taskExecutionTotalTime(MICROSECONDS));
        taskWaitingTimeUs.record(taskWaitingTotalTime(MICROSECONDS));
        tasksExecuted.record(tasksExecuted());
        tasksRequested.record(tasksRequested());
      },
      taskExecutionTimeUs, taskWaitingTimeUs,
      tasksExecuted, tasksRequested
    );
  }
}
