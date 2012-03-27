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
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author irengrig
 *         Date: 3/16/11
 *         Time: 2:41 PM
 */
public class ShowAllAffectedGenericAction extends AnAction {
  public ShowAllAffectedGenericAction() {
    super("Show all affected files", null, IconLoader.getIcon("/vcs/allRevisions.png"));
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
    if (! isInLocalFSHack(virtualFile) && ! canPresentNonLocal(project, vcsKey, virtualFile)) return;

    final String title = VcsBundle.message("paths.affected.in.revision",
                                           revision instanceof ShortVcsRevisionNumber
                                               ? ((ShortVcsRevisionNumber) revision).toShortString()
                                               :  revision.asString());
    final CommittedChangeList[] list = new CommittedChangeList[1];
    final VcsException[] exc = new VcsException[1];
    ProgressManager.getInstance().run(new Task.Backgroundable(project, title, true, BackgroundFromStartOption.getInstance()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
          if (isInLocalFSHack(virtualFile)) {
            final Pair<CommittedChangeList, FilePath> pair = provider.getOneList(virtualFile, revision);
            if (pair != null) {
              list[0] = pair.getFirst();
            }
          } else {
            final RepositoryLocation local = provider.getForNonLocal(virtualFile);
            if (local != null) {
              final List<CommittedChangeList> changes = provider.getCommittedChanges(provider.createDefaultSettings(), local, 1);
              if (changes != null && changes.size() == 1) {
                list[0] = changes.get(0);
              }
            }
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
          instance.showChangesListBrowser(list[0], virtualFile, title);
        }
      }
    });
  }

  private static boolean isInLocalFSHack(final VirtualFile vf) {
    if (vf.isInLocalFileSystem()) return true;
    final String url = vf.getPresentableUrl();
    return ! url.contains("://") && ! url.contains(":\\\\");
  }

  private static String failedText(VirtualFile virtualFile, VcsRevisionNumber revision) {
    return "Show all affected files for " + virtualFile.getPath() + " at " + revision.asString() + " failed";
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
    boolean enabled = (e.getData(VcsDataKeys.VCS_FILE_REVISION) != null) && (revisionVirtualFile != null);
    enabled = enabled && (isInLocalFSHack(revisionVirtualFile) || canPresentNonLocal(project, vcsKey, revisionVirtualFile));
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean canPresentNonLocal(Project project, VcsKey key, final VirtualFile file) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(key.getName());
    if (vcs == null) return false;
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    if (provider == null) return false;
    return provider.getForNonLocal(file) != null;
  }
}
