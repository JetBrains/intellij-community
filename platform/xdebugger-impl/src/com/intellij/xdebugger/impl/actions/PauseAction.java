// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xdebugger.impl.XDebuggerUtilImpl.performDebuggerAction;

/**
 * @deprecated Don't use this action directly, implement your own instead by using XDebugSession.pause
 */
// This action should be migrated to FrontendPauseAction when debugger toolwindow won't be LUXed in Remote Dev
@Deprecated
public class PauseAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    XDebugSession session = DebuggerUIUtil.getSession(e);
    if (session == null || !((XDebugSessionImpl)session).isPauseActionSupported()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Project project = e.getProject();
    if (project == null || session.isStopped() || session.isPaused()) {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    XDebugSession session = DebuggerUIUtil.getSession(e);
    if (session != null) {
      performDebuggerAction(e, () -> session.pause());
    }
  }
}
