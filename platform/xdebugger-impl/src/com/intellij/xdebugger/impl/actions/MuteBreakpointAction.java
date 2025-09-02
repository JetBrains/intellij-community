// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerCustomMuteBreakpointHandler;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerMuteBreakpointsHandler;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xdebugger.impl.actions.handlers.XDebuggerCustomMuteBreakpointHandlerKt.getAvailableCustomMuteBreakpointHandler;

public class MuteBreakpointAction extends ToggleAction implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  private static final DebuggerToggleActionHandler ourHandler = new XDebuggerMuteBreakpointsHandler();

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(final @NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return false;
    }
    XDebuggerCustomMuteBreakpointHandler customHandler = getAvailableCustomMuteBreakpointHandler(project, e);
    if (customHandler != null) {
      return customHandler.areBreakpointsMuted(project, e);
    }
    return ourHandler.isSelected(project, e);
  }

  @Override
  public void setSelected(final @NotNull AnActionEvent e, final boolean state) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    XDebuggerCustomMuteBreakpointHandler customHandler = getAvailableCustomMuteBreakpointHandler(project, e);
    if (customHandler != null) {
      customHandler.updateBreakpointsState(project, e, state);
      return;
    }
    ourHandler.setSelected(project, e, state);
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    XDebuggerCustomMuteBreakpointHandler customHandler = getAvailableCustomMuteBreakpointHandler(project, e);
    if (customHandler != null) {
      e.getPresentation().setEnabled(true);
      return;
    }
    e.getPresentation().setEnabled(ourHandler.isEnabled(project, e));
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
