// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsPreservingExecutor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Executes an operation surrounding it by stash-unstash procedure.
 * I.e. stashes changes, executes the operation and then unstashes it.
 */
public class GitPreservingExecutor implements VcsPreservingExecutor {

  @Override
  public boolean execute(@NotNull Project project,
                         @NotNull Collection<? extends VirtualFile> rootsToSave,
                         @NotNull String operationTitle,
                         @NotNull ProgressIndicator indicator,
                         @NotNull Runnable operation) {
    GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);
    if (!areRootsUnderGitWithInitialCommit(rootsToSave, gitRepositoryManager)) {
      return false;
    }
    new GitPreservingProcess(project, Git.getInstance(), rootsToSave, operationTitle, "",
                             GitVcsSettings.getInstance(project).updateChangesPolicy(),
                             indicator, operation).execute();
    return true;
  }

  private static boolean areRootsUnderGitWithInitialCommit(@NotNull Collection<? extends VirtualFile> roots,
                                                           @NotNull GitRepositoryManager gitRepositoryManager) {
    return ContainerUtil.all(roots, root -> {
      GitRepository gitRepository = gitRepositoryManager.getRepositoryForRoot(root);
      if (gitRepository == null) {
        return false;
      }
      if (gitRepository.getCurrentRevision() == null) {
        return false;
      }
      return true;
    });
  }
}
