// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.openapi.application.AccessToken;
import kotlin.coroutines.CoroutineContext;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

@Internal
public final class ContextCallable<V> implements Callable<V> {

  /**
   * Whether this callable is expected to be at the bottom of the stacktrace.
   */
  private final boolean myRoot;
  private final @NotNull CoroutineContext myParentContext;
  private final @NotNull Callable<? extends V> myCallable;

  public ContextCallable(boolean root, @NotNull Callable<? extends V> callable) {
    myRoot = root;
    myParentContext = ThreadContext.currentThreadContext();
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
