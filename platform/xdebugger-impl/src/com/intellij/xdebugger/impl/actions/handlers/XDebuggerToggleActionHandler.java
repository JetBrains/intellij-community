// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class XDebuggerToggleActionHandler extends DebuggerToggleActionHandler {
  @Override
  public final boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    return isEnabled(session, event);
  }

  @Override
  public boolean isSelected(@NotNull final Project project, final AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    return isSelected(session, event);
  }

  @Override
  public void setSelected(@NotNull final Project project, final AnActionEvent event, final boolean state) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    setSelected(session, event, state);
  }

  protected abstract boolean isEnabled(@Nullable XDebugSession session, final AnActionEvent event);

  protected abstract boolean isSelected(@Nullable XDebugSession session, final AnActionEvent event);

  protected abstract void setSelected(@Nullable XDebugSession session, final AnActionEvent event, boolean state);
}
