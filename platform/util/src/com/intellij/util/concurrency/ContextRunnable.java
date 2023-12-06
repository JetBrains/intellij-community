// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

final class ContextRunnable implements Runnable {

  private final boolean myRoot;
  private final @NotNull CoroutineContext myParentContext;
  private final @NotNull Runnable myRunnable;

  @Async.Schedule
  ContextRunnable(boolean root, @NotNull CoroutineContext context, @NotNull Runnable runnable) {
    myRoot = root;
    myParentContext = context;
    myRunnable = runnable;
  }

  @Async.Execute
  @Override
  public void run() {
    try (AccessToken ignored = ThreadContext.installThreadContext(myParentContext, !myRoot)) {
      myRunnable.run();
    }
  }

  public Runnable getDelegate() {
    return myRunnable;
  }

  @Override
  public String toString() {
    return myRunnable.toString();
  }
}
