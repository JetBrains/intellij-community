// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangesViewRefresher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

/**
 * Untracked and ignored files in Git are not queried within the normal refresh procedure - they are watched separately.
 * I.e. to make a full refresh when user presses "Refresh" in the Changes View it is needed to prepare untracked files for refresh as well.
 */
public class GitChangesViewRefresher implements ChangesViewRefresher {
  @Override
  public void refresh(@NotNull Project project) {
    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
    for (GitRepository repository : repositoryManager.getRepositories()) {
      repository.getUntrackedFilesHolder().invalidate();
      repository.getResolvedConflictsFilesHolder().invalidate();
    }
  }
}
