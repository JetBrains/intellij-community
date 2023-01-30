// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

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
}
