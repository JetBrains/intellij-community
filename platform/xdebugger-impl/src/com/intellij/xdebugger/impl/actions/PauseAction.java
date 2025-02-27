// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xdebugger.impl.XDebuggerUtilImpl.performDebuggerAction;

/**
 * @deprecated Don't use this action directly, implement your own instead by using XDebugSession.pause
 */
@Deprecated(forRemoval = true)
public class PauseAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    XDebugSession session = DebuggerUIUtil.getSession(e);
    if (session == null || !((XDebugSessionImpl)session).isPauseActionSupported()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (isPauseResumeMerged()) {
      e.getPresentation().setEnabledAndVisible(isEnabled(e));
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(isEnabled(e));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    XDebugSession session = DebuggerUIUtil.getSession(e);
    if (project == null || session == null || session.isStopped() || session.isPaused()) {
      return false;
    }
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    XDebugSession session = DebuggerUIUtil.getSession(e);
    if (session != null) {
      performDebuggerAction(e, () -> session.pause());
    }
  }

  @ApiStatus.Internal
  public static boolean isPauseResumeMerged() {
    return Registry.is("debugger.merge.pause.and.resume");
  }
}
