// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class XDebuggerMuteBreakpointsHandler extends XDebuggerToggleActionHandler {
  @Override
  protected boolean isEnabled(final @Nullable XDebugSession session, final AnActionEvent event) {
    if (session instanceof XDebugSessionImpl) {
      return !((XDebugSessionImpl)session).isReadOnly();
    }
    return true;
  }

  @Override
  protected boolean isSelected(final @Nullable XDebugSession session, final AnActionEvent event) {
    if (session != null) {
      return session.areBreakpointsMuted();
    }
    else {
      XDebugSessionData data = event.getData(XDebugSessionData.DATA_KEY);
      if (data != null) {
        return data.isBreakpointsMuted();
      }
    }
    return false;
  }

  @Override
  protected void setSelected(final @Nullable XDebugSession session, final AnActionEvent event, final boolean state) {
    if (session != null) {
      session.setBreakpointMuted(state);
    }
    else {
      XDebugSessionData data = event.getData(XDebugSessionData.DATA_KEY);
      if (data != null) {
        data.setBreakpointsMuted(state);
      }
    }
  }
}
