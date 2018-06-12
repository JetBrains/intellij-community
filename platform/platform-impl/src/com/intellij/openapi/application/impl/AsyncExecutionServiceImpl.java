// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.AsyncExecutionService;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * @author peter
 */
public class AsyncExecutionServiceImpl extends AsyncExecutionService {
  @NotNull
  @Override
  public AppUIExecutor createUIExecutor(@NotNull ModalityState modalityState) {
    return new AppUIExecutorImpl(modalityState);
  }

  @NotNull
  @Override
  public <T> NonBlockingReadAction<T> buildNonBlockingReadAction(Callable<T> computation) {
    return new NonBlockingReadActionImpl<>(null, null, () -> false, computation);
  }
}
