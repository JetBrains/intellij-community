// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class StepIntoAction extends XDebuggerActionBase {
  @Override
  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getStepIntoHandler();
  }

  @Override
  protected boolean performWithHandler(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      return true;
    }
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session != null && session.getDebugProcess().isPreferSmartStepInto()) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        DebuggerActionHandler smartStepIntoHandler = support.getSmartStepIntoHandler();
        if (smartStepIntoHandler.isEnabled(project, e)) {
          smartStepIntoHandler.perform(project, e);
          return true;
        }
      }
    }
    return super.performWithHandler(e);
  }
}
