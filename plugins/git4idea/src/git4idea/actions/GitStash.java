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
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
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
        try (AccessToken ignored = DvcsUtil.workingTreeChangeStarted(project)) {
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
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("stash.action.name");
  }
}
