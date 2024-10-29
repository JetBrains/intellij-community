// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

final class ContextBiConsumer<T, U> implements BiConsumer<T, U> {

  private final @NotNull ChildContext myChildContext;
  private final @NotNull BiConsumer<T, U> myRunnable;

  @Async.Schedule
  ContextBiConsumer(@NotNull ChildContext context, @NotNull BiConsumer<T, U> callable) {
    myChildContext = context;
    myRunnable = callable;
  }

  @Async.Execute
  @Override
  public void accept(T t, U u) {
    try (AccessToken ignored = ThreadContext.resetThreadContext()) {
      myChildContext.runInChildContext(() -> {
        myRunnable.accept(t, u);
      });
    }
  }
}
