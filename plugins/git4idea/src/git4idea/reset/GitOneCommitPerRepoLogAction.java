// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.reset;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.VcsLogOneCommitPerRepoAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GitOneCommitPerRepoLogAction extends VcsLogOneCommitPerRepoAction<GitRepository> {

  @Override
  protected @NotNull AbstractRepositoryManager<GitRepository> getRepositoryManager(@NotNull Project project) {
    return GitRepositoryManager.getInstance(project);
  }

  @Override
  protected @Nullable GitRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) {
    return getRepositoryManager(project).getRepositoryForRootQuick(root);
  }
}
