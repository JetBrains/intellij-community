// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class RemoveAllBreakpointsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
      WriteAction.run(() -> Arrays.stream(breakpointManager.getAllBreakpoints()).forEach(breakpointManager::removeBreakpoint));
    }
  }
}
