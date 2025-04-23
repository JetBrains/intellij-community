// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class ViewBreakpointsAction
 * @author Jeka
 */
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import org.jetbrains.annotations.NotNull;

public class ViewBreakpointsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);

    XBreakpoint<?> initialBreakpoint;
    if (editor != null) {
      initialBreakpoint = XBreakpointUtil.findSelectedBreakpoint(project, editor).second;
    }
    else {
      initialBreakpoint = null;
    }

    BreakpointsDialogFactory.getInstance(project).showDialog(initialBreakpoint);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    event.getPresentation().setEnabled(event.getProject() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}