// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.*;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * @author peter
 */
public class AsyncExecutionServiceImpl extends AsyncExecutionService {
  private static long ourWriteActionCounter = 0;

  public AsyncExecutionServiceImpl() {
    Application app = ApplicationManager.getApplication();
    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void writeActionStarted(@NotNull Object action) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourWriteActionCounter++;
      }
    }, app);
  }

  @NotNull
  @Override
  protected ExpirableExecutor createExecutor(@NotNull Executor executor) {
    return new ExpirableExecutorImpl(executor);
  }

  @NotNull
  @Override
  protected AppUIExecutor createUIExecutor(@NotNull ModalityState modalityState) {
    return new AppUIExecutorImpl(modalityState, ExecutionThread.EDT);
  }

  @NotNull
  @Override
  protected AppUIExecutor createWriteThreadExecutor(@NotNull ModalityState modalityState) {
    return new AppUIExecutorImpl(modalityState, ExecutionThread.WT);
  }

  @NotNull
  @Override
  public <T> NonBlockingReadAction<T> buildNonBlockingReadAction(@NotNull Callable<T> computation) {
    return new NonBlockingReadActionImpl<>(computation);
  }

  static long getWriteActionCounter() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return ourWriteActionCounter;
  }
}
