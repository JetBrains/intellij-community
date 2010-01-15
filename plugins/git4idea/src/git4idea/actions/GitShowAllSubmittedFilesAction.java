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
package git4idea.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitFileRevision;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Initial code for show submitted files action, this action is accessed from history view
 */
public class GitShowAllSubmittedFilesAction extends AnAction implements DumbAware {

  /**
   * A constructor
   */
  public GitShowAllSubmittedFilesAction() {
    super(GitBundle.message("show.all.paths.affected.action.name"), null, IconLoader.getIcon("/icons/allRevisions.png"));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    e.getPresentation().setEnabled((e.getData(VcsDataKeys.VCS_FILE_REVISION) != null) && (revisionVirtualFile != null));
  }

  /**
   * {@inheritDoc}
   */
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if ((revision != null) && (revisionVirtualFile != null)) {
      final GitFileRevision gitRevision = ((GitFileRevision)revision);
      showSubmittedFiles(project, gitRevision, revisionVirtualFile);
    }
  }

  /**
   * Show submitted files
   *
   * @param project  a project
   * @param revision a file revision
   * @param file     file affected by the revision
   */
  public static void showSubmittedFiles(final Project project, final VcsFileRevision revision, final VirtualFile file) {
    showSubmittedFiles(project, revision.getRevisionNumber().asString(), file);
  }

  /**
   * Show submitted files
   *
   * @param project  a project
   * @param revision a revision number
   * @param file     file affected by the revision
   */
  public static void showSubmittedFiles(final Project project, final String revision, final VirtualFile file) {
    GitVcs.getInstance(project).runInBackground(new Task.Backgroundable(project, GitBundle.message("changes.retrieving", revision)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        try {
          VirtualFile vcsRoot = GitUtil.getGitRoot(file);
          final CommittedChangeList changeList = GitChangeUtils.getRevisionChanges(project, vcsRoot, revision);
          if (changeList != null) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              public void run() {
                AbstractVcsHelper.getInstance(project)
                  .showChangesListBrowser(changeList, GitShowAllSubmittedFilesAction.getTitle(revision));
              }
            });
          }
        }
        catch (final VcsException e) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
              GitUIUtil.showOperationError(project, e, "git show");
            }
          });
        }
      }
    });
  }


  /**
   * Get dialog title
   *
   * @param revNumber a revision number for the dialog
   * @return a dialog title
   */
  private static String getTitle(final String revNumber) {
    return GitBundle.message("paths.affected.title", revNumber);
  }
}
