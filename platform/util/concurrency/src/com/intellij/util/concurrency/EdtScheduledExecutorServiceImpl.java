// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An {@link ExecutorService} implementation which delegates tasks to the EDT for execution.
 */
final class EdtScheduledExecutorServiceImpl extends SchedulingWrapper implements EdtScheduledExecutorService {
  static final EdtScheduledExecutorService INSTANCE = new EdtScheduledExecutorServiceImpl();

  private EdtScheduledExecutorServiceImpl() {
    super(EdtExecutorServiceImpl.INSTANCE, ((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).getDelayQueue());
  }

  @Override
  public @NotNull ScheduledFuture<?> schedule(@NotNull Runnable command, @NotNull ModalityState modalityState, long delay, TimeUnit unit) {
    return delayedExecute(new MyScheduledFutureTask<Void>(
      ThreadContext.captureThreadContext(command),
      null,
      triggerTime(delay, unit)) {
      @Override
      public boolean executeMeInBackendExecutor() {
        // optimization: can be cancelled already
        if (!isDone()) {
          ApplicationManager.getApplication().invokeLater(this, modalityState, __ -> {
            return isCancelled();
          });
        }
        return true;
      }
    });
  }

  @Override
  protected void futureDone(@NotNull Future<?> task) {
    if (EdtExecutorServiceImpl.shouldManifestExceptionsImmediately()) {
      ConcurrencyUtil.manifestExceptionsIn(task);
    }
  }

  // stubs
  @Override
  public void shutdown() {
    AppScheduledExecutorService.notAllowedMethodCall();
  }

  @Override
  public @NotNull @Unmodifiable List<Runnable> shutdownNow() {
    return AppScheduledExecutorService.notAllowedMethodCall();
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
    AppScheduledExecutorService.notAllowedMethodCall();
    return false;
  }
}
