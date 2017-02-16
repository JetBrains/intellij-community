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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffContext;
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.StandardDiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class CompareRevisionsFromHistoryAction extends AnAction {
  private static final String COMPARE_TEXT = "Compare";
  private static final String COMPARE_DESCRIPTION = "Compare selected versions";
  private static final String DIFF_TEXT = "Show Diff";
  private static final String DIFF_DESCRIPTION = "Show diff with previous version";
  @NotNull private final DiffFromHistoryHandler myDiffHandler;

  public CompareRevisionsFromHistoryAction() {
    this(new StandardDiffFromHistoryHandler());
  }

  public CompareRevisionsFromHistoryAction(@NotNull DiffFromHistoryHandler diffHandler) {
    super(DIFF_TEXT, DIFF_DESCRIPTION, AllIcons.Actions.Diff);
    myDiffHandler = diffHandler;
  }

  public void update(@NotNull AnActionEvent e) {
    if (e.getProject() == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    VcsFileRevision[] revisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (filePath == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    boolean severalRevisionsSelected = revisions != null && revisions.length > 1;
    if (severalRevisionsSelected) {
      e.getPresentation().setText(COMPARE_TEXT);
      e.getPresentation().setDescription(COMPARE_DESCRIPTION);
    }
    else {
      e.getPresentation().setText(DIFF_TEXT);
      e.getPresentation().setDescription(DIFF_DESCRIPTION);
    }

    e.getPresentation().setEnabledAndVisible((severalRevisionsSelected && !filePath.isDirectory()) ||
                                             e.getData(VcsDataKeys.CHANGES) != null && e.getData(VcsDataKeys.VCS_REVISION_NUMBER) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsFileRevision[] revisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
    FilePath filePath = e.getRequiredData(VcsDataKeys.FILE_PATH);
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    if (revisions != null && revisions.length > 1) {
      VcsFileRevision olderRevision = revisions[revisions.length - 1];
      VcsFileRevision newestRevision = revisions[0];
      myDiffHandler.showDiffForTwo(project, filePath, olderRevision, newestRevision);
    }
    else {
      Collection<Change> changes = Arrays.asList(e.getRequiredData(VcsDataKeys.CHANGES));
      if (filePath.isDirectory()) {
        VcsDiffUtil.showChangesDialog(project, "Changes in " +
                                               e.getRequiredData(VcsDataKeys.VCS_REVISION_NUMBER).asString() +
                                               " for " + filePath.getName(),
                                      ContainerUtil.newArrayList(changes));
      }
      else {
        ShowDiffAction.showDiffForChange(project, changes, 0, new ShowDiffContext());
      }
    }
  }
}
