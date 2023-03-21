// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

final class ContextCallable<V> implements Callable<V> {

  /**
   * Whether this callable is expected to be at the bottom of the stacktrace.
   */
  private final boolean myRoot;
  private final @NotNull CoroutineContext myParentContext;
  private final @NotNull Callable<? extends V> myCallable;

  ContextCallable(boolean root, @NotNull CoroutineContext context, @NotNull Callable<? extends V> callable) {
    myRoot = root;
    myParentContext = context;
    myCallable = callable;
  }

  @Override
  public V call() throws Exception {
    if (myRoot) {
      ThreadContext.checkUninitializedThreadContext();
    }
    try (AccessToken ignored = ThreadContext.replaceThreadContext(myParentContext)) {
      return myCallable.call();
    }
  }
}
