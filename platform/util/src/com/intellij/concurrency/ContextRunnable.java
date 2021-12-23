// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.application.AccessToken;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class ContextRunnable implements Runnable {

  private final @NotNull CoroutineContext myParentContext;
  private final @NotNull Runnable myRunnable;

  public ContextRunnable(@NotNull Runnable runnable) {
    myParentContext = ThreadContext.currentThreadContext();
    myRunnable = runnable;
  }

  @Override
  public void run() {
    ThreadContext.checkUninitializedThreadContext();
    try (AccessToken ignored = ThreadContext.resetThreadContext(myParentContext)) {
      myRunnable.run();
    }
  }
}
