// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Executes tasks synchronously immediately after they submitted
 */
@ApiStatus.Internal
public final class SameThreadExecutorService extends AbstractExecutorService {
  private volatile boolean isTerminated;

  @Override
  public void shutdown() {
    isTerminated = true;
  }

  @Override
  public boolean isShutdown() {
    return isTerminated;
  }

  @Override
  public boolean isTerminated() {
    return isTerminated;
  }

  @Override
  public boolean awaitTermination(long theTimeout, @NotNull TimeUnit theUnit) {
    if (!isShutdown()) {
      throw new IllegalStateException("Must call shutdown*() before awaitTermination()");
    }
    return true;
  }

  @Contract(pure = true)
  @Override
  public @NotNull List<Runnable> shutdownNow() {
    shutdown();
    return Collections.emptyList();
  }

  @Override
  public void execute(@NotNull Runnable command) {
    if (isShutdown()) {
      throw new IllegalStateException("Must not call execute() after pool is shut down");
    }
    command.run();
  }
}
