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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;

class RemoveBreakpointGutterIconAction extends DumbAwareAction {
  private final XBreakpointBase<?,?,?> myBreakpoint;

  RemoveBreakpointGutterIconAction(XBreakpointBase<?, ?, ?> breakpoint) {
    super(XDebuggerBundle.message("xdebugger.remove.line.breakpoint.action.text"));
    myBreakpoint = breakpoint;
    AnAction action = ActionManager.getInstance().getAction("ToggleLineBreakpoint");
    copyShortcutFrom(action);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    XDebuggerUtil.getInstance().removeBreakpoint(myBreakpoint.getProject(), myBreakpoint);
  }
}