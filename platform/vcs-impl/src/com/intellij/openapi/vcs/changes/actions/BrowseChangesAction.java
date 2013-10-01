/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesFilterDialog;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class BrowseChangesAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    assert vFile != null;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vFile);
    assert vcs != null;
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    assert provider != null;
    ChangeBrowserSettings settings = provider.createDefaultSettings();
    CommittedChangesFilterDialog dlg = new CommittedChangesFilterDialog(project, provider.createFilterUI(true), settings);
    dlg.show();
    if (!dlg.isOK()) return;

    int maxCount = 0;
    if (!settings.isAnyFilterSpecified()) {
      int rc = Messages.showYesNoCancelDialog(project, VcsBundle.message("browse.changes.no.filter.prompt"), VcsBundle.message("browse.changes.title"),
                                     VcsBundle.message("browse.changes.show.recent.button"),
                                     VcsBundle.message("browse.changes.show.all.button"),
                                     CommonBundle.getCancelButtonText(),
                                    Messages.getQuestionIcon());
      if (rc == 2) {
        return;
      }
      if (rc == 0) {
        maxCount = 50;
      }
    }

    AbstractVcsHelper.getInstance(project).openCommittedChangesTab(vcs, vFile, settings, maxCount, null);
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isActionEnabled(e));
  }

  private static boolean isActionEnabled(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;
    VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (vFile == null) return false;
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(vFile);
    if (vcs == null || vcs.getCommittedChangesProvider() == null || !vcs.allowsRemoteCalls(vFile)) {
      return false;
    }
    FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(vFile);
    return AbstractVcs.fileInVcsByFileStatus(project, filePath);
  }
}