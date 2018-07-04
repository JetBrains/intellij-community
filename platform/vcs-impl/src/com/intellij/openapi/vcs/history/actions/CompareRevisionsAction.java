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
package com.intellij.openapi.vcs.history.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.*;
import org.jetbrains.annotations.NotNull;

public class CompareRevisionsAction implements AnActionExtensionProvider {

  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsFileRevision[] revisions = e.getRequiredData(VcsDataKeys.VCS_FILE_REVISIONS);
    FilePath filePath = e.getRequiredData(VcsDataKeys.FILE_PATH);
    VcsHistoryProvider provider = e.getRequiredData(VcsDataKeys.HISTORY_PROVIDER);

    DiffFromHistoryHandler customDiffHandler = provider.getHistoryDiffHandler();
    DiffFromHistoryHandler diffHandler = customDiffHandler == null ? new StandardDiffFromHistoryHandler() : customDiffHandler;

    if (revisions.length == 2) {
      diffHandler.showDiffForTwo(e.getRequiredData(CommonDataKeys.PROJECT), filePath, revisions[1], revisions[0]);
    }
    else if (revisions.length == 1) {
      VcsFileRevision previousRevision = e.getRequiredData(FileHistoryPanelImpl.PREVIOUS_REVISION_FOR_DIFF);
      if (revisions[0] != null) {
        diffHandler.showDiffForOne(e, e.getRequiredData(CommonDataKeys.PROJECT), filePath, previousRevision, revisions[0]);
      }
    }
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.HISTORY_SESSION) != null;
  }

  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setText(VcsBundle.message("action.name.compare"));
    e.getPresentation().setDescription(VcsBundle.message("action.description.compare"));

    VcsFileRevision[] revisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
    VcsHistorySession historySession = e.getData(VcsDataKeys.HISTORY_SESSION);
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsHistoryProvider provider = e.getData(VcsDataKeys.HISTORY_PROVIDER);

    boolean isEnabled;
    if (revisions == null || historySession == null || filePath == null || provider == null) {
      isEnabled = false;
    }
    else if (revisions.length == 1) {
      isEnabled = historySession.isContentAvailable(revisions[0]) &&
                  e.getData(FileHistoryPanelImpl.PREVIOUS_REVISION_FOR_DIFF) != null;
    }
    else if (revisions.length == 2) {
      isEnabled = historySession.isContentAvailable(revisions[0]) &&
                  historySession.isContentAvailable(revisions[revisions.length - 1]);
    }
    else {
      isEnabled = false;
    }

    e.getPresentation().setEnabled(isEnabled);
  }
}
