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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.ui.history.FileHistoryUi;
import com.intellij.vcs.log.ui.history.VcsLogFileRevision;
import org.jetbrains.annotations.NotNull;

public class ShowAllAffectedFromHistoryAction extends FileHistorySingleCommitAction {

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.VCS_FILE_REVISION) != null &&
           e.getData(VcsDataKeys.VCS_VIRTUAL_FILE) != null &&
           e.getData(VcsDataKeys.VCS) != null;
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryUi ui,
                               @NotNull VcsFullCommitDetails details,
                               @NotNull AnActionEvent e) {
    VcsLogFileRevision revision = ui.createRevision(details);
    VirtualFile vcsVirtualFile = ui.createVcsVirtualFile(details);

    if (revision != null && vcsVirtualFile != null) {
      AbstractVcsHelper.getInstance(project).loadAndShowCommittedChangesDetails(project, revision.getRevisionNumber(), vcsVirtualFile,
                                                                                e.getRequiredData(VcsDataKeys.VCS),
                                                                                revision.getChangedRepositoryPath(),
                                                                                false);
    }
  }
}
