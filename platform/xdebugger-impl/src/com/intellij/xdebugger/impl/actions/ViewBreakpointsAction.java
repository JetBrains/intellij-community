// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class ViewBreakpointsAction
 * @author Jeka
 */
package com.intellij.xdebugger.impl.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;

public class ViewBreakpointsAction extends AnAction implements AnAction.TransparentUpdate, DumbAware {
  private Object myInitialBreakpoint;

  public ViewBreakpointsAction(){
    this(ActionsBundle.actionText(XDebuggerActions.VIEW_BREAKPOINTS), null);
  }

  public ViewBreakpointsAction(String name, Object initialBreakpoint) {
    super(name);
    myInitialBreakpoint = initialBreakpoint;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    if (myInitialBreakpoint == null) {
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (editor != null) {
        myInitialBreakpoint = XBreakpointUtil.findSelectedBreakpoint(project, editor).second;
      }
    }

    BreakpointsDialogFactory.getInstance(project).showDialog(myInitialBreakpoint);
    myInitialBreakpoint = null;
  }

  @Override
  public void update(AnActionEvent event){
    event.getPresentation().setEnabled(event.getProject() != null);
  }

}