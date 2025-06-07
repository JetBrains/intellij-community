// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcs.commit.VcsPathsToRefreshProvider
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager

/**
 * see [git4idea.repo.GitRepository.getResolvedConflictsFilesHolder]
 */
internal class GitPathsToRefreshProvider : VcsPathsToRefreshProvider {
  override fun getVcsName(): String = GitVcs.NAME

  override fun collectPathsToRefresh(project: Project): Collection<FilePath> {
    val repositoryManager = GitRepositoryManager.getInstance(project)
    val repositories = repositoryManager.repositories

    return repositories.flatMap { repository ->
      repository.resolvedConflictsFilesHolder.resolvedConflictsFilePaths()
    }
  }
}
