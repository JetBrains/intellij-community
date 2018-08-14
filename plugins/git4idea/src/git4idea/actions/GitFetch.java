// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitFetcher;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GitFetch extends GitRepositoryAction {
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("fetch.action.name");
  }

  @Override
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot) {
    GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        new GitFetcher(project, indicator, true).fetchRootsAndNotify(GitUtil.getRepositoriesFromRoots(repositoryManager, gitRoots),
                                                                     null, true);
      }
    });
  }
}
