// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    if (point == null) { // disabled gutter icons for example
      point = new Point(gutterComponent.getWidth(),
                        editor.visualPositionToXY(editor.getCaretModel().getVisualPosition()).y + editor.getLineHeight() / 2);
    }
    doShowPopup(project, gutterComponent, point, breakpoint);
  }

  public void editBreakpoint(@NotNull Project project, @NotNull JComponent parent, @NotNull Point whereToShow, @NotNull BreakpointItem breakpoint) {
    doShowPopup(project, parent, whereToShow, breakpoint.getBreakpoint());
  }
}
