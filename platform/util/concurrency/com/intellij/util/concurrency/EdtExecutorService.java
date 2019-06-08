// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 * An {@link ExecutorService} implementation which
 * delegates tasks to the EDT for execution.
 */
public abstract class EdtExecutorService extends AbstractExecutorService {
  @NotNull
  public static EdtExecutorService getInstance() {
    return EdtExecutorServiceImpl.INSTANCE;
  }

  @NotNull
  public static ScheduledExecutorService getScheduledExecutorInstance() {
    return EdtScheduledExecutorService.getInstance();
  }

  public abstract void execute(@NotNull Runnable command, @NotNull ModalityState modalityState);
  @NotNull
  public abstract Future<?> submit(@NotNull Runnable command, @NotNull ModalityState modalityState);
  @NotNull
  public abstract <T> Future<T> submit(@NotNull Callable<T> task, @NotNull ModalityState modalityState);
}
