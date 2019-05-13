// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitStashDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Git stash action
 */
public class GitStash extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @Override
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isFreezedWithNotification("Can not stash changes now")) return;
    GitStashDialog d = new GitStashDialog(project, gitRoots, defaultRoot);
    if (!d.showAndGet()) {
      return;
    }

    new Task.Backgroundable(project, GitBundle.getString("stashing.title"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try (AccessToken ignored = DvcsUtil.workingTreeChangeStarted(project, getActionName())) {
          GitCommandResult result = Git.getInstance().runCommand(d.handler());
          if (!result.success()) {
            VcsNotifier.getInstance(project).notifyError(GitBundle.getString("stashing.title"),
                                                         result.getErrorOutputAsHtmlString());
          }
        }
      }

      @Override
      public void onFinished() {
        VfsUtil.markDirtyAndRefresh(false, true, false, d.getGitRoot());
      }
    }.queue();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("stash.action.name");
  }
}
