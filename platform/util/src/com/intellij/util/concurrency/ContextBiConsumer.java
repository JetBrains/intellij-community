// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.util.ThrowableRunnable;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

final class ContextBiConsumer<T, U> implements BiConsumer<T, U> {

  /**
   * Whether this callable is expected to be at the bottom of the stacktrace.
   */
  private final boolean myRoot;
  private final @NotNull CoroutineContext myParentContext;
  private final @NotNull BiConsumer<T, U> myRunnable;

  @Async.Schedule
  ContextBiConsumer(boolean root, @NotNull CoroutineContext context, @NotNull BiConsumer<T, U> callable) {
    myRoot = root;
    myParentContext = context;
    myRunnable = callable;
  }

  @Async.Execute
  @Override
  public void accept(T t, U u) {
    try (AccessToken ignored = ThreadContext.installThreadContext(myParentContext, !myRoot)) {
      myRunnable.accept(t, u);
    }
  }
}
