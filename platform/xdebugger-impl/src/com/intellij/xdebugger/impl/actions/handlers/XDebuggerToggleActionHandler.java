// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class XDebuggerToggleActionHandler extends DebuggerToggleActionHandler {
  @Override
  public final boolean isEnabled(final @NotNull Project project, final AnActionEvent event) {
    return isEnabled(DebuggerUIUtil.getSessionProxy(event), event);
  }

  @Override
  public boolean isSelected(final @NotNull Project project, final AnActionEvent event) {
    return isSelected(DebuggerUIUtil.getSessionProxy(event), event);
  }

  @Override
  public void setSelected(final @NotNull Project project, final AnActionEvent event, final boolean state) {
    setSelected(DebuggerUIUtil.getSessionProxy(event), event, state);
  }

  protected abstract boolean isEnabled(@Nullable XDebugSessionProxy session, final AnActionEvent event);

  protected abstract boolean isSelected(@Nullable XDebugSessionProxy session, final AnActionEvent event);

  protected abstract void setSelected(@Nullable XDebugSessionProxy session, final AnActionEvent event, boolean state);
}
