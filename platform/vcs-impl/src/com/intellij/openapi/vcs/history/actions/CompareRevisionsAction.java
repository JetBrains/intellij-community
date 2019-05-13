// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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

  @Override
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
