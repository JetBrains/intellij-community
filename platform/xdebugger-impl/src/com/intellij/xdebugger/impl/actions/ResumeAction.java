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

import com.intellij.execution.actions.ChooseDebugConfigurationPopupAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ResumeAction extends XDebuggerActionBase implements DumbAware {
  @Override
  protected boolean isEnabled(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;

    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session != null && !session.isStopped()) {
      return session.isPaused();
    }
    return !ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (!performWithHandler(e)) {
      Project project = getEventProject(e);
      if (project != null && !DumbService.isDumb(project)) {
        new ChooseDebugConfigurationPopupAction().actionPerformed(e);
      }
    }
  }

  @NotNull
  protected DebuggerActionHandler getHandler(@NotNull final DebuggerSupport debuggerSupport) {
    return debuggerSupport.getResumeActionHandler();
  }
}
