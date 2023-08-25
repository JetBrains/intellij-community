// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncExecutionServiceImpl extends AsyncExecutionService {
  private static final AtomicLong ourWriteActionCounter = new AtomicLong();

  public AsyncExecutionServiceImpl() {
    Application app = ApplicationManager.getApplication();
    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void writeActionStarted(@NotNull Object action) {
        ourWriteActionCounter.incrementAndGet();
      }
    }, app);
  }

  /**
   * @deprecated use coroutines and their cancellation mechanism instead
   */
  @Deprecated(forRemoval = true)
  @Override
  protected @NotNull ExpirableExecutor createExecutor(@NotNull Executor executor) {
    return new ExpirableExecutorImpl(executor);
  }

  @Override
  protected @NotNull AppUIExecutor createUIExecutor(@NotNull ModalityState modalityState) {
    return new AppUIExecutorImpl(modalityState, ExecutionThread.EDT);
  }

  @Override
  protected @NotNull AppUIExecutor createWriteThreadExecutor(@NotNull ModalityState modalityState) {
    return new AppUIExecutorImpl(modalityState, ExecutionThread.WT);
  }

  @Override
  public @NotNull <T> NonBlockingReadAction<T> buildNonBlockingReadAction(@NotNull Callable<? extends T> computation) {
    return new NonBlockingReadActionImpl<>(computation);
  }

  @ApiStatus.Internal
  public static long getWriteActionCounter() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return ourWriteActionCounter.get();
  }
}
