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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import com.intellij.vcs.log.history.FileHistoryUiProperties;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcsUtil.VcsUtil;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

public class ShowOtherBranchesAction extends BooleanPropertyToggleAction {

  public ShowOtherBranchesAction() {
    super("Show All Branches", "Switch between showing only current branch and all branches", VcsLogIcons.ShowOtherBranches);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return FileHistoryUiProperties.SHOW_ALL_BRANCHES;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Project project = e.getProject();
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsLogManager logManager = e.getData(VcsLogInternalDataKeys.LOG_MANAGER);
    if (project != null && logManager != null && filePath != null) {
      VcsLogIndex index = logManager.getDataManager().getIndex();
      VirtualFile root = VcsUtil.getVcsRootFor(project, filePath);
      if (root != null && !index.isIndexed(root)) {
        e.getPresentation().setEnabled(false);
      }
    }
  }
}
