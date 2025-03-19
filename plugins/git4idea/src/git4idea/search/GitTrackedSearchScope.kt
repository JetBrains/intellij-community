// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.ProjectScopeImpl
import git4idea.i18n.GitBundle
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
import git4idea.index.vfs.filePath
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitUntrackedFilesHolder
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting

internal class GitTrackedSearchScope(
  private val project: Project,
  private val rootToFilesHolders: Map<VirtualFile, Pair<GitUntrackedFilesHolder, GitRepositoryIgnoredFilesHolder>>,
) : ProjectScopeImpl(project, FileIndexFacade.getInstance(project)) {
  @Nls
  override fun getDisplayName(): String = GitBundle.message("search.scope.project.git.tracked")

  override fun contains(file: VirtualFile): Boolean = super.contains(file) && isTracked(file)

  @VisibleForTesting
  fun isTracked(file: VirtualFile): Boolean {
    val filePath = file.filePath()
    val gitRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file) ?: return false
    val (untrackedFilesHolder, ignoredFilesHolder) = rootToFilesHolders[gitRoot] ?: return false
    return !untrackedFilesHolder.containsUntrackedFile(filePath) && !ignoredFilesHolder.containsFile(filePath)
  }

  companion object {
    private val LOG = thisLogger()

    @VisibleForTesting
    internal fun getSearchScope(project: Project): GitTrackedSearchScope? {
      val repositories = GitRepositoryManager.getInstance(project).repositories
      if (repositories.isEmpty()) return null
      val rootToFilesHolders = getFilesHoldersMapping(repositories) ?: return null
      return GitTrackedSearchScope(project, rootToFilesHolders)
    }

    private fun getFilesHoldersMapping(repositories: List<GitRepository>): Map<VirtualFile, Pair<GitUntrackedFilesHolder, GitRepositoryIgnoredFilesHolder>>? =
      repositories.associate { repo ->
        if (!repo.untrackedFilesHolder.isInitialized || !repo.ignoredFilesHolder.initialized) {
          if (LOG.isDebugEnabled) {
            LOG.debug("Untracked files holder is not initialized for $repo")
          }
          return null
        }
        repo.root to Pair(repo.untrackedFilesHolder, repo.ignoredFilesHolder)
      }
  }
}
