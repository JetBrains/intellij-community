// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public abstract class XDebuggerActionHandler extends DebuggerActionHandler {
  @Override
  public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
    XDebugSessionProxy session = DebuggerUIUtil.getSessionProxy(event);
    if (session != null) {
      perform(session, event.getDataContext());
    }
  }

  @Override
  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    if (LightEdit.owns(project)) return false;
    XDebugSessionProxy session = DebuggerUIUtil.getSessionProxy(event);
    return session != null && isEnabled(session, event.getDataContext());
  }

  /**
   * Override {@link XDebuggerActionHandler#isEnabled(XDebugSessionProxy, DataContext)} instead
   */
  @ApiStatus.Obsolete
  protected boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    throw new AbstractMethodError("Override isEnabled(XDebugSessionProxy, DataContext) in " + getClass().getName());
  }

  /**
   * Override {@link XDebuggerActionHandler#perform(XDebugSessionProxy, DataContext)} instead
   */
  @ApiStatus.Obsolete
  @ApiStatus.OverrideOnly
  protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    throw new AbstractMethodError("Override perform(XDebugSessionProxy, DataContext) in " + getClass().getName());
  }

  @ApiStatus.Internal
  protected boolean isEnabled(@NotNull XDebugSessionProxy session, @NotNull DataContext dataContext) {
    if (session instanceof XDebugSessionProxy.Monolith monolith) {
      return isEnabled(monolith.getSession(), dataContext);
    }
    return false;
  }

  @ApiStatus.Internal
  @ApiStatus.OverrideOnly
  protected void perform(@NotNull XDebugSessionProxy session, @NotNull DataContext dataContext) {
    if (session instanceof XDebugSessionProxy.Monolith monolith) {
      perform(monolith.getSession(), dataContext);
    }
    else {
      Logger.getInstance(getClass())
        .warn("Action perform is skipped: isEnabled returned true, " +
              "but perform method was not adapted for XDebugSessionProxy usage");
    }
  }
}
