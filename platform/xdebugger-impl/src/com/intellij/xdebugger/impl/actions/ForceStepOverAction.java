// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

public class ForceStepOverAction extends XDebuggerActionBase {
  private static final XDebuggerSuspendedActionHandler ourHandler = new XDebuggerSuspendedActionHandler() {
    @Override
    protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
      session.stepOver(true);
    }
  };

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }
}
