/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;

public class RefreshStatuses extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project != null) {
      VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
    }
  }

  public void update(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    boolean isEnabled = project != null &&
        ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 0;
    e.getPresentation().setEnabled(isEnabled);
    e.getPresentation().setVisible(isEnabled);
  }
}
