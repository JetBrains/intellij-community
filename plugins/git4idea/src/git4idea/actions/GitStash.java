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

public class GitStash extends GitRepositoryAction {

  @Override
  protected void perform(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification("Can not stash changes now")) return;
    GitStashDialog d = new GitStashDialog(project, gitRoots, defaultRoot);
    if (!d.showAndGet()) {
      return;
    }

    new Task.Backgroundable(project, GitBundle.getString("stashing.title"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try (AccessToken ignored = DvcsUtil.workingTreeChangeStarted(project, getActionName())) {
          GitCommandResult result = Git.getInstance().runCommand(d.handler());
          if (result.success()) {
            VfsUtil.markDirtyAndRefresh(false, true, false, d.getGitRoot());
          }
          else {
            VcsNotifier.getInstance(project).notifyError("Stash Failed", result.getErrorOutputAsHtmlString());
          }
        }
      }
    }.queue();
  }

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("stash.action.name");
  }
}
