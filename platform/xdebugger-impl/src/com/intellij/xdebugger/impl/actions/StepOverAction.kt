// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Don't inherit from the action, implement your own
 */
// This action should be migrated to FrontendStepOverAction when debugger toolwindow won't be LUXed in Remote Dev
@Deprecated(forRemoval = true)
public class StepOverAction extends XDebuggerActionBase implements DumbAware {
  private static final DebuggerActionHandler ourHandler = new XDebuggerSuspendedActionHandler() {
    @Override
    protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
      session.stepOver(false);
    }
  };

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }
}
