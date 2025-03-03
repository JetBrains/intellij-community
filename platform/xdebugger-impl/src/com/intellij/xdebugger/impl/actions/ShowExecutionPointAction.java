// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ShowExecutionPointAction extends XDebuggerActionBase {
  private static final XDebuggerSuspendedActionHandler ourHandler = new XDebuggerSuspendedActionHandler() {
    @Override
    protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
      session.showExecutionPoint();
    }
  };

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }
}
