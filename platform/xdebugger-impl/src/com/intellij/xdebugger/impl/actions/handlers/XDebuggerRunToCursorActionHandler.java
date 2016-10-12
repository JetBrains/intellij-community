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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
*/
public class XDebuggerRunToCursorActionHandler extends XDebuggerSuspendedActionHandler {
  private final boolean myIgnoreBreakpoints;

  public XDebuggerRunToCursorActionHandler(final boolean ignoreBreakpoints) {
    myIgnoreBreakpoints = ignoreBreakpoints;
  }

  @Override
  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    return super.isEnabled(session, dataContext) && XDebuggerUtilImpl.getCaretPosition(session.getProject(), dataContext) != null;
  }

  @Override
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(session.getProject(), dataContext);
    if (position != null) {
      session.runToPosition(position, myIgnoreBreakpoints);
    }
  }
}
