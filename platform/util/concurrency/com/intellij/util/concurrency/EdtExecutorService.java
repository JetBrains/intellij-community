// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 * An {@link ExecutorService} implementation which
 * delegates tasks to the EDT for execution.
 */
public abstract class EdtExecutorService extends AbstractExecutorService {
  public static @NotNull EdtExecutorService getInstance() {
    return EdtExecutorServiceImpl.INSTANCE;
  }

  public static @NotNull ScheduledExecutorService getScheduledExecutorInstance() {
    return EdtScheduledExecutorService.getInstance();
  }

  public abstract void execute(@NotNull Runnable command, @NotNull ModalityState modalityState);

  public abstract void execute(@NotNull Runnable command, @NotNull ModalityState modalityState, @NotNull Condition<?> expired);

  public abstract @NotNull Future<?> submit(@NotNull Runnable command, @NotNull ModalityState modalityState);

  public abstract @NotNull <T> Future<T> submit(@NotNull Callable<T> task, @NotNull ModalityState modalityState);
}
