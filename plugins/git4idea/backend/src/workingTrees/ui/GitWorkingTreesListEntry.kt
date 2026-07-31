// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.util.io.FileUtil
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

// A row of the Worktrees tab. Entries reference the *shared* [GitRepositoryModel]
internal sealed interface GitWorkingTreesListEntry {
  val repository: GitRepositoryModel
}

internal enum class GitRepositoryKind { TOP_LEVEL, SUBMODULE, NESTED }

internal data class GitRepositoryHeader(
  override val repository: GitRepositoryModel,
  @param:Nls val presentableName: String,
  val kind: GitRepositoryKind,
) : GitWorkingTreesListEntry

internal data class GitWorktreeRow(
  override val repository: GitRepositoryModel,
  val gitWorkingTree: GitWorkingTree,
  @param:Nls val presentableBranchName: String,
  @param:Nls val location: String,
  val indented: Boolean,
) : GitWorkingTreesListEntry

internal fun buildWorkingTreesEntries(
  repositories: List<GitRepositoryModel>,
  tagNameForCommit: (repository: GitRepositoryModel, headHash: String) -> String? = { _, _ -> null },
  repositoryKind: (repository: GitRepositoryModel) -> GitRepositoryKind = { GitRepositoryKind.TOP_LEVEL },
): List<GitWorkingTreesListEntry> {
  val withHeaders = repositories.size > 1
  return repositories.sortedBy { it.root.path }.flatMap { repositoryEntries(it, withHeaders, tagNameForCommit, repositoryKind) }
}

private fun repositoryEntries(
  repository: GitRepositoryModel,
  withHeader: Boolean,
  tagNameForCommit: (repository: GitRepositoryModel, headHash: String) -> String?,
  repositoryKind: (repository: GitRepositoryModel) -> GitRepositoryKind,
): List<GitWorkingTreesListEntry> {
  val entries = mutableListOf<GitWorkingTreesListEntry>()
  if (withHeader) {
    entries.add(GitRepositoryHeader(repository, repository.shortName, repositoryKind(repository)))
  }
  repository.state.workingTrees.sortedByDescending { it.isMain }.forEach { wt ->
    entries.add(GitWorktreeRow(
      repository = repository,
      gitWorkingTree = wt,
      presentableBranchName = presentableBranchName(wt) { headHash -> tagNameForCommit(repository, headHash) },
      location = FileUtil.getLocationRelativeToUserHome(wt.path.path),
      indented = withHeader,
    ))
  }
  return entries
}

@Nls
private fun presentableBranchName(worktree: GitWorkingTree, tagName: (headHash: String) -> String?): String {
  worktree.currentBranch?.let { return it.name }
  worktree.headHash?.let { headHash -> tagName(headHash)?.let { return it } }
  return GitBundle.message("toolwindow.working.trees.tab.detached.working.tree.branch.text")
}

internal fun resolveSelectedRepository(
  selected: List<GitWorkingTreesListEntry>,
  allRepositories: List<GitRepositoryModel>,
): GitRepositoryModel? {
  val repositories = selected.map { it.repository }.distinct()
  return when {
    repositories.size == 1 -> repositories.single()
    repositories.isEmpty() -> allRepositories.singleOrNull()
    else -> null
  }
}
