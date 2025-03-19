// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.execution.ExecutionException;
import org.jetbrains.annotations.NotNull;

/**
 * Factory class for {@link XDebugProcess} implementation. Used by {@link XDebuggerManager} to start a new debugging session
 */
public abstract class XDebugProcessStarter {
  /**
   * Create a new instance of {@link XDebugProcess} implementation. Note that {@code session} isn't initialized when this method is
   * called so in order to perform code depending on {@code session} parameter override {@link XDebugProcess#sessionInitialized} method
   * @param session session to be passed to {@link XDebugProcess#XDebugProcess} constructor
   * @return new {@link XDebugProcess} instance
   */
  public abstract @NotNull XDebugProcess start(@NotNull XDebugSession session) throws ExecutionException;
}