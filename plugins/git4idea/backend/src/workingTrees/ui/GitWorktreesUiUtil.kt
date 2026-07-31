// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryIdCache
import git4idea.repo.getTagsForCommit

/** Stateless helpers backing the Worktrees tab; reads from the shared [GitRepositoryModel]s populated via [GitRepositoriesHolder]. */
internal object GitWorktreesUiUtil {

  fun getRepositories(project: Project): List<GitRepositoryModel> {
    if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) {
      return emptyList()
    }

    val holder = GitRepositoriesHolder.getInstance(project)
    if (!holder.initialized) {
      return emptyList()
    }

    // A linked working tree can be registered as its own VCS root; collapse it into its underlying repository.
    return GitWorkingTreesUtil.mergeLinkedWorktreeRepositories(
      holder.getAll(),
      rootPath = { it.root.path },
      commonGitDirPath = { it.commonGitDirPath },
      workingTrees = { it.state.workingTrees },
    )
  }

  fun buildEntries(project: Project): List<GitWorkingTreesListEntry> {
    val repositories = getRepositories(project)
    return buildWorkingTreesEntries(
      repositories,
      tagNameForCommit = { model, headHash -> findTagNameForCommit(project, model, headHash) },
      repositoryKind = { model -> resolveRepositoryKind(model, repositories) },
    )
  }

  fun resolveSelectedRepositoryModel(project: Project, selected: List<GitWorkingTreesListEntry>): GitRepositoryModel? =
    resolveSelectedRepository(selected, getRepositories(project))

  fun resolveSelectedBackendRepository(project: Project, selected: List<GitWorkingTreesListEntry>): GitRepository? =
    resolveSelectedRepositoryModel(project, selected)?.let { findBackendRepository(project, it) }

  fun findBackendRepository(project: Project, model: GitRepositoryModel): GitRepository? =
    GitRepositoryIdCache.getInstance(project).get(model.repositoryId)

  fun anyRepositoryHasMultipleWorktrees(project: Project): Boolean =
    getRepositories(project).any { it.state.workingTrees.size > 1 }

  fun isEmpty(project: Project): Boolean = getRepositories(project).isEmpty()

  private fun findTagNameForCommit(project: Project, model: GitRepositoryModel, headHash: String): String? =
    findBackendRepository(project, model)?.tagsHolder?.getTagsForCommit(headHash)?.firstOrNull()?.name

  private fun resolveRepositoryKind(model: GitRepositoryModel, allRepositories: List<GitRepositoryModel>): GitRepositoryKind {
    if (model.isSubmodule) return GitRepositoryKind.SUBMODULE
    val path = model.root.path
    val nested = allRepositories.any { it.repositoryId != model.repositoryId && FileUtil.isAncestor(it.root.path, path, true) }
    return if (nested) GitRepositoryKind.NESTED else GitRepositoryKind.TOP_LEVEL
  }
}
