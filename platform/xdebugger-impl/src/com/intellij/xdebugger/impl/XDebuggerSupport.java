// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerMuteBreakpointsHandler;
import org.jetbrains.annotations.NotNull;

public class XDebuggerSupport extends DebuggerSupport {
  private final DebuggerToggleActionHandler myMuteBreakpointsHandler;

  public XDebuggerSupport() {
    myMuteBreakpointsHandler = new XDebuggerMuteBreakpointsHandler();
  }

  @Override
  public @NotNull DebuggerToggleActionHandler getMuteBreakpointsHandler() {
    return myMuteBreakpointsHandler;
  }
}
