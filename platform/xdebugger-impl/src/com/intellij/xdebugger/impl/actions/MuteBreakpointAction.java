// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

public class MuteBreakpointAction extends ToggleAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(final @NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        DebuggerToggleActionHandler handler = support.getMuteBreakpointsHandler();
        if (handler.isEnabled(project, e)) {
          return handler.isSelected(project, e);
        }
      }
    }
    return false;
  }

  @Override
  public void setSelected(final @NotNull AnActionEvent e, final boolean state) {
    Project project = e.getProject();
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        DebuggerToggleActionHandler handler = support.getMuteBreakpointsHandler();
        if (handler.isEnabled(project, e)) {
          handler.setSelected(project, e, state);
          return;
        }
      }
    }
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        DebuggerToggleActionHandler handler = support.getMuteBreakpointsHandler();
        if (handler.isEnabled(project, e)) {
          e.getPresentation().setEnabled(true);
          return;
        }
      }
    }
    e.getPresentation().setEnabled(false);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
