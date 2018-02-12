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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.GitResetDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The reset action
 */
public class GitResetHead extends GitRepositoryAction {
  /**
   * {@inheritDoc}
   */
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("reset.action.name");
  }

  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull Project project,
                         @NotNull List<VirtualFile> gitRoots,
                         @NotNull VirtualFile defaultRoot) {
    GitResetDialog d = new GitResetDialog(project, gitRoots, defaultRoot);
    if (!d.showAndGet()) {
      return;
    }

    new Task.Backgroundable(project, GitBundle.getString("resetting.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try (AccessToken ignored = DvcsUtil.workingTreeChangeStarted(project)) {
          GitCommandResult result = Git.getInstance().runCommand(d.handler());
          if (!result.success()) {
            VcsNotifier.getInstance(project).notifyError(GitBundle.getString("resetting.title"),
                                                         result.getErrorOutputAsHtmlString());
          }
        }
      }

      @Override
      public void onFinished() {
        GitRepositoryManager.getInstance(project).updateRepository(d.getGitRoot());
        VfsUtil.markDirtyAndRefresh(true, true, false, d.getGitRoot());
      }
    }.queue();
  }
}
