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
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreatePatchFromHistoryActionProvider implements AnActionExtensionProvider {
  private final boolean mySilentClipboard;

  private CreatePatchFromHistoryActionProvider(boolean silentClipboard) {
    mySilentClipboard = silentClipboard;
  }

  public static class Dialog extends CreatePatchFromHistoryActionProvider {
    public Dialog() {
      super(false);
    }
  }

  public static class Clipboard extends CreatePatchFromHistoryActionProvider {
    public Clipboard() {
      super(true);
    }
  }
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    if (project == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    List<CommitId> selectedCommits = ui.getVcsLog().getSelectedCommits();
    String commitMessage = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    e.getPresentation().setEnabled(!selectedCommits.isEmpty() && commitMessage != null);
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUtil.triggerUsage(e);

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    FileHistoryUi ui = e.getRequiredData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    String commitMessage = e.getRequiredData(VcsDataKeys.PRESET_COMMIT_MESSAGE);

    ui.getVcsLog().requestSelectedDetails(detailsList -> {
      List<Change> changes = ui.collectChanges(detailsList, false);
      CreatePatchFromChangesAction.createPatch(project, commitMessage, changes, mySilentClipboard);
    });
  }
}
