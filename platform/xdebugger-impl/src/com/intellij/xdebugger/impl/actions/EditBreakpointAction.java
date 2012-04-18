/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class EditBreakpointAction extends AnAction {
  protected Object myBreakpoint;
  private GutterIconRenderer myBreakpointGutterRenderer;

  public EditBreakpointAction(@Nullable Object breakpoint, @Nullable GutterIconRenderer breakpointGutterRenderer) {
    super(message());
    myBreakpoint = breakpoint;
    myBreakpointGutterRenderer = breakpointGutterRenderer;
  }

  private static String message() {
    return XDebuggerBundle.message("xdebugger.view.breakpoint.edit.action");
  }

  public EditBreakpointAction() {
    super(message());
  }

  protected Object findBreakpoint(DataContext context) {
    Editor editor = PlatformDataKeys.EDITOR.getData(context);
    Project project = PlatformDataKeys.PROJECT.getData(context);
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(context);
    if (editor == null || project == null || file == null) return null;

    int line = editor.getCaretModel().getLogicalPosition().line;
    XLineBreakpointType<?>[] lineBreakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    for (XLineBreakpointType<?> type : lineBreakpointTypes) {
      XLineBreakpoint<? extends XBreakpointProperties> breakpoint =
        XDebuggerManager.getInstance(project).getBreakpointManager().findBreakpointAtLine(type, file, line);
      if (breakpoint != null) {
        return breakpoint;
      }
    }
    return null;
  }

  protected GutterIconRenderer findGutterIconRenderer(DataContext context, Object breakpoint) {
    if (breakpoint instanceof XLineBreakpoint) {
      RangeHighlighter highlighter = ((XLineBreakpointImpl)breakpoint).getHighlighter();
      if (highlighter != null) {
        return highlighter.getGutterIconRenderer();
      }
    }
    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      final EditorGutterComponentEx gutterComponent = ((EditorEx)editor).getGutterComponentEx();
      Point point = gutterComponent.getPoint(myBreakpointGutterRenderer);
      if (point != null) {
        final Icon icon = myBreakpointGutterRenderer.getIcon();
        Point whereToShow =
          new Point(point.x + icon.getIconWidth() / 2 + gutterComponent.getIconsAreaWidth(), point.y + icon.getIconHeight() / 2);
        doShowPopup(project, gutterComponent, whereToShow);
      }
    }
  }

  protected void doShowPopup(Project project, EditorGutterComponentEx gutterComponent, Point whereToShow) {
    DebuggerUIUtil.showXBreakpointEditorBalloon(project, whereToShow, gutterComponent, false, (XBreakpoint)myBreakpoint);
  }
}
