// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.concurrency;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public final class SequentialTaskExecutor {
  private SequentialTaskExecutor() {
  }

  @NotNull
  public static ExecutorService createSequentialApplicationPoolExecutor(@NonNls @NotNull String name) {
    return AppExecutorUtil.createBoundedApplicationPoolExecutor(name, 1);
  }

  @NotNull
  public static ExecutorService createSequentialApplicationPoolExecutor(@NonNls @NotNull String name,  @NotNull Executor executor) {
    return AppExecutorUtil.createBoundedApplicationPoolExecutor(name, executor, 1);
  }
}
