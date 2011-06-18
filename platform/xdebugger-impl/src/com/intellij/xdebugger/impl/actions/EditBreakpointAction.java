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
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class EditBreakpointAction extends AnAction {
  private XBreakpointBase myBreakpoint;
  private GutterIconRenderer myBreakpointGutterRenderer;

  public EditBreakpointAction(String text,
                              @NotNull XBreakpointBase breakpoint,
                              GutterIconRenderer breakpointGutterRenderer) {
    super(text);
    myBreakpoint = breakpoint;
    myBreakpointGutterRenderer = breakpointGutterRenderer;
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
      final Icon icon = myBreakpointGutterRenderer.getIcon();
      DebuggerUIUtil.showBreakpointEditorBalloon(project, new Point(point.x + icon.getIconWidth()/2 + gutterComponent.getIconsAreaWidth(), point.y + icon.getIconHeight()/2), gutterComponent, false, myBreakpoint);
    }
  }
}
