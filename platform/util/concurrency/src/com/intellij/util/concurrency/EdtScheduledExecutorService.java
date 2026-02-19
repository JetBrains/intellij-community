// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An {@link ExecutorService} implementation which schedules tasks to the EDT for execution.
  * @deprecated Use coroutines.
  */
@Deprecated
public interface EdtScheduledExecutorService extends ScheduledExecutorService {
  static @NotNull EdtScheduledExecutorService getInstance() {
    return EdtScheduledExecutorServiceImpl.INSTANCE;
  }

  @NotNull
  ScheduledFuture<?> schedule(@NotNull Runnable command, @NotNull ModalityState modalityState, long delay, TimeUnit unit);
}
