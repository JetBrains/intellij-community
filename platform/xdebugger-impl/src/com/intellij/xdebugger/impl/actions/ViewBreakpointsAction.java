/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    Presentation presentation = event.getPresentation();
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(true);
  }

}