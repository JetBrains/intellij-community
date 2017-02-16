/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ShowAllAffectedFromHistoryAction extends FileHistorySingleCommitAction {

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.VCS_FILE_REVISION) != null &&
           e.getData(VcsDataKeys.VCS_VIRTUAL_FILE) != null &&
           e.getData(VcsDataKeys.VCS) != null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsKey vcsKey = e.getRequiredData(VcsDataKeys.VCS);
    VcsFileRevision revision = e.getRequiredData(VcsDataKeys.VCS_FILE_REVISION);
    VirtualFile revisionVirtualFile = e.getRequiredData(VcsDataKeys.VCS_VIRTUAL_FILE);

    AbstractVcsHelper.getInstance(project).loadAndShowCommittedChangesDetails(project, revision.getRevisionNumber(), revisionVirtualFile,
                                                                              vcsKey, revision.getChangedRepositoryPath(),
                                                                              false);
  }
}
