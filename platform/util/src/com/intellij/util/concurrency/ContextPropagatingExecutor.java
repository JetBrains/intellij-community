// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ThreadContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

interface ContextPropagatingExecutor extends Executor {

  /**
   * Same as {@link #execute(Runnable)}, but doesn't propagate the {@link ThreadContext#currentThreadContext() thread context}.
   */
  void executeRaw(@NotNull Runnable command);
}
