// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

final class ContextFunction<T, R> implements Function<T, R> {

  private final @NotNull CoroutineContext myParentContext;
  private final @NotNull Function<T, R> myFunction;

  @Async.Schedule
  ContextFunction(@NotNull CoroutineContext context, @NotNull Function<T, R> function) {
    myParentContext = context;
    myFunction = function;
  }

  @Async.Execute
  @Override
  public R apply(T arg) {
    try (AccessToken ignored = ThreadContext.installThreadContext(myParentContext, true)) {
      return myFunction.apply(arg);
    }
  }
  @Override
  public String toString() {
    return myFunction.toString();
  }
}
