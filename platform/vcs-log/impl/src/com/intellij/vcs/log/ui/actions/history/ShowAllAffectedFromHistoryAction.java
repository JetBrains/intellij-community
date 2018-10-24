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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.DataGetter;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.history.FileHistoryUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ShowAllAffectedFromHistoryAction extends FileHistorySingleCommitAction<VcsFullCommitDetails> {

  @NotNull
  @Override
  protected List<VcsFullCommitDetails> getSelection(@NotNull FileHistoryUi ui) {
    return ui.getVcsLog().getSelectedDetails();
  }

  @NotNull
  @Override
  protected DataGetter<VcsFullCommitDetails> getDetailsGetter(@NotNull FileHistoryUi ui) {
    return ui.getLogData().getCommitDetailsGetter();
  }

  @Override
  protected void performAction(@NotNull Project project,
                               @NotNull FileHistoryUi ui,
                               @NotNull VcsFullCommitDetails detail,
                               @NotNull AnActionEvent e) {
    VirtualFile file = FileHistoryUtil.createVcsVirtualFile(ui.createRevision(detail));
    AbstractVcsHelper.getInstance(project).showChangesListBrowser(VcsLogUtil.createCommittedChangeList(detail), file,
                                                                  "Paths affected by commit " + detail.getId().toShortString());
  }
}
