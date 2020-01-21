// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;

public interface TaskExecutor {
  @NotNull
  Future<?> executeTask(@NotNull Runnable task);
}
