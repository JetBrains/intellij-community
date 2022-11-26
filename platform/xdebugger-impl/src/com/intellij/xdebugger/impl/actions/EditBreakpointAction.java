// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

public class EditBreakpointAction extends XDebuggerActionBase implements DumbAware {

  public static class ContextAction extends DumbAwareAction {
    private final GutterIconRenderer myRenderer;
    private final Object myBreakpoint;
    private final DebuggerSupport myDebuggerSupport;

    public ContextAction(GutterIconRenderer breakpointRenderer, Object breakpoint, DebuggerSupport debuggerSupport) {
      super(ActionsBundle.actionText("EditBreakpoint"));
      myRenderer = breakpointRenderer;
      myBreakpoint = breakpoint;
      myDebuggerSupport = debuggerSupport;
      AnAction action = ActionManager.getInstance().getAction("ViewBreakpoints");
      copyShortcutFrom(action);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Editor editor = e.getData(CommonDataKeys.EDITOR);
      Project project = getEventProject(e);
      if (editor == null || project == null) return;
      myDebuggerSupport.getEditBreakpointAction().editBreakpoint(project, editor, myBreakpoint, myRenderer);
    }
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return debuggerSupport.getEditBreakpointAction();
  }
}
