// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * An {@link ExecutorService} implementation which
 * delegates tasks to the EDT for execution.
 */
final class EdtExecutorServiceImpl extends EdtExecutorService {
  private EdtExecutorServiceImpl() {
  }

  @Override
  public void execute(@NotNull Runnable command) {
    Application application = ApplicationManager.getApplication();
    execute(command, application == null ? ModalityState.NON_MODAL : application.getAnyModalityState());
  }

  @Override
  public void execute(@NotNull Runnable command, @NotNull ModalityState modalityState) {
    Application application = ApplicationManager.getApplication();
    execute(command, modalityState, application == null ? Conditions.alwaysFalse() : application.getDisposed());
  }

  @Override
  public void execute(@NotNull Runnable command, @NotNull ModalityState modalityState, @NotNull Condition<?> expired) {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      EventQueue.invokeLater(command);
    }
    else {
      application.invokeLater(command, modalityState, expired);
    }
  }

  @Override
  protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
    return newTaskFor(Executors.callable(runnable, value));
  }

  @Override
  protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
    FutureTask<T> task = AppScheduledExecutorService.capturePropagationAndCancellationContext(callable);
    if (shouldManifestExceptionsImmediately()) {
      return new FlippantFuture<>(task);
    }
    return task;
  }

  @NotNull
  @Override
  public Future<?> submit(@NotNull Runnable command, @NotNull ModalityState modalityState) {
    RunnableFuture<?> future = newTaskFor(command, null);
    execute(future, modalityState);
    return future;
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task, @NotNull ModalityState modalityState) {
    RunnableFuture<T> future = newTaskFor(task);
    execute(future, modalityState);
    return future;
  }

  @Override
  public void shutdown() {
    AppScheduledExecutorService.notAllowedMethodCall();
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
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

  static final EdtExecutorService INSTANCE = new EdtExecutorServiceImpl();

  static boolean shouldManifestExceptionsImmediately() {
    return ApplicationManager.getApplication() != null && ApplicationManager.getApplication().isUnitTestMode();
  }
}
