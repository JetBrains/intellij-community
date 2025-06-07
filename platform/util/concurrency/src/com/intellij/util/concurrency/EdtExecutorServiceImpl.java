// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * An {@link ExecutorService} implementation which
 * delegates tasks to the EDT for execution.
 */
final class EdtExecutorServiceImpl extends EdtExecutorService {
  static final EdtExecutorService INSTANCE = new EdtExecutorServiceImpl();

  private EdtExecutorServiceImpl() {
  }

  static boolean shouldManifestExceptionsImmediately() {
    Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  @Override
  public void execute(@NotNull Runnable command) {
    Application app = ApplicationManager.getApplication();
    if (app == null) {
      EventQueue.invokeLater(command);
    }
    else {
      app.invokeLater(command, ModalityState.any());
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
