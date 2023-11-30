// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see XDebuggerManager#TOPIC
 */
public interface XDebuggerManagerListener {
  default void processStarted(@NotNull XDebugProcess debugProcess) {
  }

  default void processStopped(@NotNull XDebugProcess debugProcess) {
  }

  default void currentSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
  }
}