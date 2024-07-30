// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.ProjectScopeImpl
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import git4idea.i18n.GitBundle
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
import git4idea.index.vfs.filePath
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting

private val LOG = logger<GitSearchScopeProvider>()

internal class GitSearchScopeProvider : SearchScopeProvider {
  override fun getGeneralSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> =
    listOfNotNull(GitIgnoreSearchScope.getSearchScope(project))
}

internal class GitIgnoreSearchScope(
  private val project: Project,
  private val rootToIgnoredFiles: Map<VirtualFile, GitRepositoryIgnoredFilesHolder>,
) : ProjectScopeImpl(project, FileIndexFacade.getInstance(project)) {
  private val singleIgnoredFilesHolder: GitRepositoryIgnoredFilesHolder? = rootToIgnoredFiles.values.singleOrNull()

  @Nls
  override fun getDisplayName(): String = GitBundle.message("search.scope.project.git.exclude.ignored")

  override fun contains(file: VirtualFile): Boolean = super.contains(file) && !isIgnored(file)

  @VisibleForTesting
  fun isIgnored(file: VirtualFile): Boolean {
    val filePath = file.filePath()
    if (singleIgnoredFilesHolder != null) return singleIgnoredFilesHolder.containsFile(filePath)

    val gitRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file) ?: return false

    return rootToIgnoredFiles[gitRoot]?.containsFile(filePath) == true
  }

  companion object {
    @VisibleForTesting
    internal fun getSearchScope(project: Project): GitIgnoreSearchScope? {
      val repositories = GitRepositoryManager.getInstance(project).repositories
      if (repositories.isEmpty()) return null
      val repoToIgnoredFiles = getIgnoredFilesMapping(repositories) ?: return null
      return GitIgnoreSearchScope(project, repoToIgnoredFiles)
    }

    private fun getIgnoredFilesMapping(repositories: List<GitRepository>): Map<VirtualFile, GitRepositoryIgnoredFilesHolder>? =
      repositories.associate { repo ->
        if (!repo.ignoredFilesHolder.initialized) {
          if (LOG.isDebugEnabled) {
            LOG.debug("Ignored files holder is not initialized for $repo")
          }
          return null
        }
        repo.root to repo.ignoredFilesHolder
      }
  }
}