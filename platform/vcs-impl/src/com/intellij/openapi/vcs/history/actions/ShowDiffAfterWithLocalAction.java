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
import com.intellij.openapi.actionSystem.ExtendableAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class ShowDiffAfterWithLocalAction extends ExtendableAction implements DumbAware {
  private static final ExtensionPointName<AnActionExtensionProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.history.actions.ShowDiffAfterWithLocalAction.ExtensionProvider");

  public ShowDiffAfterWithLocalAction() {
    super(EP_NAME);
  }

  @Override
  public void defaultActionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    VcsRevisionNumber currentRevisionNumber = e.getRequiredData(VcsDataKeys.HISTORY_SESSION).getCurrentRevisionNumber();
    VcsFileRevision selectedRevision = e.getRequiredData(VcsDataKeys.VCS_FILE_REVISIONS)[0];
    FilePath filePath = e.getRequiredData(VcsDataKeys.FILE_PATH);
    VirtualFile virtualFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);

    if (currentRevisionNumber != null && selectedRevision != null) {
      DiffFromHistoryHandler diffHandler = ObjectUtils.notNull(e.getRequiredData(VcsDataKeys.HISTORY_PROVIDER).getHistoryDiffHandler(),
                                                               new StandardDiffFromHistoryHandler());
      diffHandler.showDiffForTwo(project, filePath,
                                 selectedRevision, new CurrentRevision(virtualFile, currentRevisionNumber));
    }
  }

  @Override
  public void defaultUpdate(@NotNull AnActionEvent e) {
    VcsFileRevision[] selectedRevisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    VcsHistorySession historySession = e.getData(VcsDataKeys.HISTORY_SESSION);

    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(selectedRevisions != null && selectedRevisions.length == 1 && virtualFile != null &&
                                   historySession != null && historySession.getCurrentRevisionNumber() != null &&
                                   historySession.isContentAvailable(selectedRevisions[0]) &&
                                   e.getData(VcsDataKeys.FILE_PATH) != null && e.getData(VcsDataKeys.HISTORY_PROVIDER) != null &&
                                   e.getData(CommonDataKeys.PROJECT) != null);
  }
}
