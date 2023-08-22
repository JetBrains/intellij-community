// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitResetDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static git4idea.GitNotificationIdsHolder.RESET_FAILED;

/**
 * The reset action
 */
public class GitResetHead extends GitRepositoryAction {
  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.message("reset.action.name");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void perform(@NotNull Project project,
                         @NotNull List<VirtualFile> gitRoots,
                         @NotNull VirtualFile defaultRoot) {
    GitResetDialog d = new GitResetDialog(project, gitRoots, defaultRoot);
    if (!d.showAndGet()) {
      return;
    }

    new Task.Backgroundable(project, GitBundle.message("resetting.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try (AccessToken ignored = DvcsUtil.workingTreeChangeStarted(project, getActionName())) {
          GitCommandResult result = Git.getInstance().runCommand(d.handler());
          if (!result.success()) {
            VcsNotifier.getInstance(project).notifyError(RESET_FAILED,
                                                         GitBundle.message("resetting.title"),
                                                         result.getErrorOutputAsHtmlString(),
                                                         true);
          }
          GitRepositoryManager.getInstance(project).updateRepository(d.getGitRoot());
          GitUtil.refreshVfsInRoot(d.getGitRoot());
        }
      }
    }.queue();
  }
}
