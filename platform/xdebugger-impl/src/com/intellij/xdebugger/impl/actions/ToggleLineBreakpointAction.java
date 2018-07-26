/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ToggleLineBreakpointAction extends XDebuggerActionBase implements DumbAware, Toggleable {
  public ToggleLineBreakpointAction() {
    super(true);
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getToggleLineBreakpointHandler();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);

    if (ActionPlaces.TOUCHBAR_GENERAL.equals(event.getPlace()))
      event.getPresentation().setIcon(AllIcons.Debugger.Db_set_breakpoint);

    final boolean selected = hasLineBreakpoint(event);
    event.getPresentation().putClientProperty(SELECTED_PROPERTY, selected);
  }

  private static boolean hasLineBreakpoint(@NotNull AnActionEvent e) {
    final Project proj = e.getProject();
    if (proj == null)
      return false;

    final XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(proj).getBreakpointManager();
    for (XSourcePosition position : XDebuggerUtilImpl.getAllCaretsPositions(proj, e.getDataContext())) {
      for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
        final VirtualFile file = position.getFile();
        final int line = position.getLine();
        if (breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
          return true;
        }
      }
    }
    return false;
  }
}
