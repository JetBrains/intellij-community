/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class XDebuggerEditBreakpointActionHandler extends EditBreakpointActionHandler {
  @Override
  protected void doShowPopup(Project project, JComponent component, Point whereToShow, Object breakpoint) {
    DebuggerUIUtil.showXBreakpointEditorBalloon(project, whereToShow, component, false, (XBreakpoint)breakpoint);
  }

  @Override
  public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return false;
    final Pair<GutterIconRenderer,Object> pair = XBreakpointUtil.findSelectedBreakpoint(project, editor);
    return pair.first != null && pair.second instanceof XLineBreakpointImpl;
  }
}
