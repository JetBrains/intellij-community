// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

final class ContextRunnable implements Runnable {

  private final @NotNull ChildContext myContext;
  private final @NotNull Runnable myRunnable;

  @Async.Schedule
  ContextRunnable(@NotNull ChildContext context, @NotNull Runnable runnable) {
    if (runnable instanceof ContextRunnable) {
      throw new IllegalArgumentException("Can not wrap ContextRunnable into ContextRunnable");
    }
    myContext = context;
    myRunnable = runnable;
  }

  @Async.Execute
  @Override
  public void run() {
    myContext.runInChildContext(myRunnable);
  }

  public Runnable getDelegate() {
    return myRunnable;
  }

  @Override
  public String toString() {
    return myRunnable.toString();
  }
}
