// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

public class XDebuggerPauseActionHandler extends XDebuggerActionHandler {
  @Override
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    session.pause();
  }

  @Override
  public boolean isHidden(@NotNull Project project, AnActionEvent event) {
    final XDebugSession session = DebuggerUIUtil.getSession(event);
    return session == null || !((XDebugSessionImpl)session).isPauseActionSupported();
  }

  @Override
  protected boolean isEnabled(@NotNull final XDebugSession session, final DataContext dataContext) {
    assert session instanceof XDebugSessionImpl;
    return ((XDebugSessionImpl)session).isPauseActionSupported() && !session.isPaused();
  }
}
