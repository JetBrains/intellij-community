// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

public abstract class XDebuggerActionHandler extends DebuggerActionHandler {
  @Override
  public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    if (session != null) {
      perform(session, event.getDataContext());
    }
  }

  @Override
  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    if (LightEdit.owns(project)) return false;
    XDebugSession session = DebuggerUIUtil.getSession(event);
    return session != null && isEnabled(session, event.getDataContext());
  }

  protected abstract boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext);

  protected abstract void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext);
}
