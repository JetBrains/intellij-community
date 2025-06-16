// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class EditBreakpointAction extends XDebuggerActionBase implements DumbAware {
  @ApiStatus.Internal
  public static final EditBreakpointActionHandler HANDLER = new XDebuggerEditBreakpointActionHandler();

  public static class ContextAction extends DumbAwareAction {
    private final GutterIconRenderer myRenderer;
    private final XBreakpointProxy myBreakpoint;

    public ContextAction(GutterIconRenderer breakpointRenderer, XBreakpointProxy breakpoint) {
      super(ActionsBundle.actionText("EditBreakpoint"));
      myRenderer = breakpointRenderer;
      myBreakpoint = breakpoint;
      AnAction action = ActionManager.getInstance().getAction("ViewBreakpoints");
      copyShortcutFrom(action);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Editor editor = e.getData(CommonDataKeys.EDITOR);
      Project project = getEventProject(e);
      if (editor == null || project == null) return;
      HANDLER.editBreakpoint(project, editor, myBreakpoint, myRenderer);
    }
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return HANDLER;
  }
}
