// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

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

  public DiskQueryRelay(@NotNull Function<? super Param, ? extends Result> function) {
    myFunction = function;
  }

  public Result accessDiskWithCheckCanceled(@NotNull Param arg) {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator == null) {
      return myFunction.apply(arg);
    }

    Future<Result> future = myTasks.computeIfAbsent(arg, eachArg -> ProcessIOExecutorService.INSTANCE.submit(() -> {
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
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future, indicator);
  }

  /**
   * Use the method for one-shot tasks; for performing multiple similar operations, prefer an instance of the class.
   * <p>
   * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
   * inside the {@code task} block.
   */
  public static <Result, E extends Exception> Result compute(@NotNull ThrowableComputable<Result, E> task) throws E, ProcessCanceledException {
    Future<Result> future = ProcessIOExecutorService.INSTANCE.submit(() -> task.compute());
    while (true) {
      try {
        ProgressManager.checkCanceled();
      }
      catch (ProcessCanceledException e) {
        future.cancel(true);
        throw e;
      }

      try {
        return future.get(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException ignore) { }
      catch (InterruptedException e) {
        throw new ProcessCanceledException(e);
      }
      catch (ExecutionException e) {
        Throwable t = e.getCause();
        ExceptionUtil.rethrowUnchecked(t);
        @SuppressWarnings("unchecked") E cause = (E)t;
        throw cause;
      }
    }
  }
}
