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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;

/**
 * @author nik
 */
public class SortValuesToggleAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().isSortValues();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    XDebuggerSettingsManager.getInstance().getDataViewSettings().setSortValues(state);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      final XDebugSession[] sessions = XDebuggerManager.getInstance(project).getDebugSessions();
      for (XDebugSession session : sessions) {
        if (session.isSuspended()) {
          session.rebuildViews();
        }
      }
    }
  }
}
