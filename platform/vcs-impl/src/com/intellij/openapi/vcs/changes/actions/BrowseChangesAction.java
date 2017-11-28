/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesFilterDialog;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesVisibilityPredicate;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.openapi.ui.Messages.*;
import static com.intellij.openapi.vcs.AbstractVcs.fileInVcsByFileStatus;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getVcsForFile;
import static com.intellij.util.ObjectUtils.notNull;

public class BrowseChangesAction extends AnAction implements DumbAware {
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);
    AbstractVcs vcs = notNull(getVcsForFile(file, project));
    CommittedChangesProvider provider = notNull(vcs.getCommittedChangesProvider());
    ChangeBrowserSettings settings =
      vcs.getConfiguration().CHANGE_BROWSER_SETTINGS.computeIfAbsent(vcs.getName(), key -> provider.createDefaultSettings());
    CommittedChangesFilterDialog dialog = new CommittedChangesFilterDialog(project, provider.createFilterUI(true), settings);

    if (dialog.showAndGet()) {
      showChanges(vcs, file, settings);
    }
  }

  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    Presentation presentation = e.getPresentation();
    if (project == null || file == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    AbstractVcs vcs = getVcsForFile(file, project);
    if (vcs == null || !CommittedChangesVisibilityPredicate.isCommittedChangesAvailable(vcs)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(isEnabled(project, vcs, file));
  }

  private static boolean isEnabled(@NotNull Project project, @NotNull AbstractVcs vcs, @NotNull VirtualFile file) {
    return vcs.allowsRemoteCalls(file) && fileInVcsByFileStatus(project, file);
  }

  private static void showChanges(@NotNull AbstractVcs vcs, @NotNull VirtualFile file, @NotNull ChangeBrowserSettings settings) {
    int maxCount = !settings.isAnyFilterSpecified() ? askMaxCount(vcs.getProject()) : 0;

    if (maxCount >= 0) {
      AbstractVcsHelper.getInstance(vcs.getProject()).openCommittedChangesTab(vcs, file, settings, maxCount, null);
    }
  }

  private static int askMaxCount(@NotNull Project project) {
    switch (showYesNoCancelDialog(project, message("browse.changes.no.filter.prompt"), message("browse.changes.title"),
                                  message("browse.changes.show.recent.button"), message("browse.changes.show.all.button"),
                                  getCancelButtonText(), getQuestionIcon())) {
      case CANCEL:
        return -1;
      case YES:
        return 50;
      default:
        return 0;
    }
  }
}