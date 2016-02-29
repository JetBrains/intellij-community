/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
*/
public class XDebuggerMuteBreakpointsHandler extends XDebuggerToggleActionHandler {
  @Override
  protected boolean isEnabled(@Nullable final XDebugSession session, final AnActionEvent event) {
    return true;
  }

  @Override
  protected boolean isSelected(@Nullable final XDebugSession session, final AnActionEvent event) {
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
  protected void setSelected(@Nullable final XDebugSession session, final AnActionEvent event, final boolean state) {
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
