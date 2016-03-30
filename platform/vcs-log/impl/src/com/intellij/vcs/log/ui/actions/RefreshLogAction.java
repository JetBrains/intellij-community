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
package com.intellij.vcs.log.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.impl.VcsLogManager;

public class RefreshLogAction extends RefreshAction {
  public RefreshLogAction() {
    super("Refresh", "Re-read Commits From Disk for All VCS Roots and Rebuild Log", AllIcons.Actions.Refresh);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLogManager.getInstance(project).getDataManager().refreshCompletely();
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      VcsLogDataManager dataManager = VcsLogManager.getInstance(project).getDataManager();
      e.getPresentation().setEnabledAndVisible(dataManager != null && e.getData(VcsLogDataKeys.VCS_LOG_UI) != null);
    }
  }
}
