/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class EditBreakpointActionHandler extends DebuggerActionHandler {

  protected abstract void doShowPopup(Project project, JComponent component, Point whereToShow, Object breakpoint);

  @Override
  public void perform(@NotNull Project project, AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    final Pair<GutterIconRenderer,Object> pair = XBreakpointUtil.findSelectedBreakpoint(project, editor);

    Object breakpoint = pair.second;
    GutterIconRenderer breakpointGutterRenderer = pair.first;

    if (breakpointGutterRenderer == null) return;
    editBreakpoint(project, editor, breakpoint, breakpointGutterRenderer);
  }

  public void editBreakpoint(@NotNull Project project, @NotNull Editor editor, @NotNull Object breakpoint, @NotNull GutterIconRenderer breakpointGutterRenderer) {
    if (BreakpointsDialogFactory.getInstance(project).isBreakpointPopupShowing()) return;
    EditorGutterComponentEx gutterComponent = ((EditorEx)editor).getGutterComponentEx();
    Point point = gutterComponent.getCenterPoint(breakpointGutterRenderer);
    if (point != null) {
      doShowPopup(project, gutterComponent, point, breakpoint);
    }
  }

  public void editBreakpoint(@NotNull Project project, @NotNull JComponent parent, @NotNull Point whereToShow, @NotNull BreakpointItem breakpoint) {
    doShowPopup(project, parent, whereToShow, breakpoint.getBreakpoint());
  }
}
