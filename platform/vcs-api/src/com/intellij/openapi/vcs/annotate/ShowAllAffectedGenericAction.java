/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 *         Date: 3/16/11
 *         Time: 2:41 PM
 */
public class ShowAllAffectedGenericAction extends AnAction {
  public ShowAllAffectedGenericAction() {
    super("Show all affected files", null, IconLoader.getIcon("/icons/allRevisions.png"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    if (vcsKey == null) return;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if ((revision != null) && (revisionVirtualFile != null)) {
      showSubmittedFiles(project, revision.getRevisionNumber(), revisionVirtualFile, vcsKey);
    }
  }

  public static void showSubmittedFiles(final Project project, final VcsRevisionNumber revision, final VirtualFile virtualFile, final VcsKey vcsKey) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsKey.getName());
    if (vcs == null) return;

    final String title = "Paths affected in revision " + revision.asString();
    final CommittedChangeList[] list = new CommittedChangeList[1];
    final VcsException[] exc = new VcsException[1];
    ProgressManager.getInstance().run(new Task.Backgroundable(project, title, true, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          final Pair<CommittedChangeList, FilePath> pair = vcs.getCommittedChangesProvider().getOneList(virtualFile, revision);
          if (pair != null) {
            list[0] = pair.getFirst();
          }
        }
        catch (VcsException e) {
          exc[0] = e;
        }
      }

      @Override
      public void onSuccess() {
        final AbstractVcsHelper instance = AbstractVcsHelper.getInstance(project);
        if (exc[0] != null) {
          instance.showError(exc[0], failedText(virtualFile, revision));
        } else if (list[0] == null) {
          Messages.showErrorDialog(project, failedText(virtualFile, revision), getTitle());
        } else {
          instance.showChangesListBrowser(list[0], title);
        }
      }
    });
  }

  private static String failedText(VirtualFile virtualFile, VcsRevisionNumber revision) {
    return new StringBuilder().append("Show all affected files for ").append(virtualFile.getPath()).append(" at ")
      .append(revision.asString()).append(" failed").toString();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    if (project == null || vcsKey == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    e.getPresentation().setEnabled((e.getData(VcsDataKeys.VCS_FILE_REVISION) != null) && (revisionVirtualFile != null));
  }
}
