// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class XDebuggerMuteBreakpointsHandler extends XDebuggerToggleActionHandler {
  @Override
  protected boolean isEnabled(final @Nullable XDebugSessionProxy session, final AnActionEvent event) {
    return session == null || !session.isReadOnly();
  }

  @Override
  protected boolean isSelected(final @Nullable XDebugSessionProxy session, final AnActionEvent event) {
    XDebugSessionData sessionData = DebuggerUIUtil.getSessionData(event);
    return sessionData != null && sessionData.isBreakpointsMuted();
  }

  @Override
  protected void setSelected(final @Nullable XDebugSessionProxy session, final AnActionEvent event, final boolean state) {
    XDebugSessionData data = DebuggerUIUtil.getSessionData(event);
    if (data != null) {
      data.setBreakpointsMuted(state);
    }
  }
}
