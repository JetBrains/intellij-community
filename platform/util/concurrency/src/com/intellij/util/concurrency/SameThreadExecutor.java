// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public final class SameThreadExecutor implements Executor {
  private SameThreadExecutor() { }

  public static final Executor INSTANCE = new SameThreadExecutor();

  @Override
  public void execute(@NotNull Runnable command) {
    command.run();
  }
}
