// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.actions.ChooseDebugConfigurationPopupAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

public class ResumeAction extends XDebuggerActionBase implements DumbAware {
  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;

    XDebugSessionImpl session = (XDebugSessionImpl)DebuggerUIUtil.getSession(e);
    if (session != null && !session.isStopped()) {
      return !session.isReadOnly() && session.isPaused();
    }
    // disable visual representation but leave the shortcut action enabled
    return e.getInputEvent() instanceof KeyEvent;
  }

  @Override
  protected boolean isHidden(@NotNull AnActionEvent event) {
    if (!PauseAction.isPauseResumeMerged()) {
      return super.isHidden(event);
    }
    return super.isHidden(event) || !isEnabled(event);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!performWithHandler(e)) {
      Project project = getEventProject(e);
      if (project != null && !DumbService.isDumb(project)) {
        new ChooseDebugConfigurationPopupAction().actionPerformed(e);
      }
    }
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return debuggerSupport.getResumeActionHandler();
  }
}
