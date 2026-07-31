// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryIdCache
import git4idea.repo.getTagsForCommit
import git4idea.repo.isSubmodule

/**
 * The single object the Worktrees tab talks to. It reads worktree state from the *shared*
 * [GitRepositoryModel]s (populated over RPC via [GitRepositoriesHolder]),
 * builds the list entries, resolves the selection, and maps a selected model back to a backend
 * [GitRepository] for the operations.
 */
internal class GitWorktreesTabModel(private val project: Project) {

  /** All git repositories of the project (peers and nested), from the shared model. */
  fun repositories(): List<GitRepositoryModel> {
    if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) {
      return emptyList()
    }

    val holder = GitRepositoriesHolder.getInstance(project)
    if (!holder.initialized) {
      return emptyList()
    }

    // A linked working tree can itself be registered as a VCS root; collapse it into its underlying repository
    // so the tab doesn't show a duplicate entry with the same worktree list.
    return GitWorkingTreesUtil.mergeLinkedWorktreeRepositories(
      holder.getAll(),
      rootPath = { it.root.path },
      workingTrees = { it.state.workingTrees },
    )
  }

  suspend fun buildEntries(): List<GitWorkingTreesListEntry> = readAction {
    // Snapshot the repository list once: repositoryKind classifies against it for every repository.
    val repositories = repositories()
    buildWorkingTreesEntries(
      repositories,
      tagNameForCommit = { model, headHash -> tagNameForCommit(model, headHash) },
      repositoryKind = { model -> repositoryKind(model, repositories) },
    )
  }

  private fun tagNameForCommit(model: GitRepositoryModel, headHash: String): String? =
    backendRepository(model)?.tagsHolder?.getTagsForCommit(headHash)?.firstOrNull()?.name

  /** Classifies a repository as top-level, a git submodule, or a plain nested repository (subdirectory). */
  private fun repositoryKind(model: GitRepositoryModel, allRepositories: List<GitRepositoryModel>): GitRepositoryKind {
    if (backendRepository(model)?.isSubmodule() == true) return GitRepositoryKind.SUBMODULE
    val path = model.root.path
    val nested = allRepositories.any { it.repositoryId != model.repositoryId && FileUtil.isAncestor(it.root.path, path, true) }
    return if (nested) GitRepositoryKind.NESTED else GitRepositoryKind.TOP_LEVEL
  }

  fun resolveSelectedRepositoryModel(selected: List<GitWorkingTreesListEntry>): GitRepositoryModel? =
    resolveSelectedRepository(selected, repositories())

  fun selectedBackendRepository(selected: List<GitWorkingTreesListEntry>): GitRepository? =
    resolveSelectedRepositoryModel(selected)?.let(::backendRepository)

  fun backendRepository(model: GitRepositoryModel): GitRepository? =
    GitRepositoryIdCache.getInstance(project).get(model.repositoryId)

  fun anyRepositoryHasMultipleWorktrees(): Boolean =
    repositories().any { it.state.workingTrees.size > 1 }

  fun isEmpty(): Boolean = repositories().isEmpty()
}
