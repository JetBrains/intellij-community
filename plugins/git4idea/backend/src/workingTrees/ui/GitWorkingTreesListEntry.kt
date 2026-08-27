// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import git4idea.workingTrees.GitWorktreePendingCreation
import org.jetbrains.annotations.Nls

// A row of the Worktrees tab. Entries reference the *shared* [GitRepositoryModel]
internal sealed interface GitWorkingTreesListEntry {
  val repository: GitRepositoryModel
  val multiRoot: Boolean
  val presentableBranchName: @Nls String
}

internal enum class GitRepositoryKind { TOP_LEVEL, SUBMODULE, NESTED }

internal data class GitWorktreeRow(
  override val repository: GitRepositoryModel,
  override val multiRoot: Boolean,
  val gitWorkingTree: GitWorkingTree,
  override val presentableBranchName: @Nls String,
  val location: @Nls String,
  val repositoryKind: GitRepositoryKind,
) : GitWorkingTreesListEntry

// A worktree whose target directory doesn't exist as a real GitWorkingTree yet: `git worktree add` is still running.
internal data class GitWorktreeCreatingRow(
  override val repository: GitRepositoryModel,
  override val multiRoot: Boolean,
  val targetPath: FilePath,
  override val presentableBranchName: @Nls String,
  val location: @Nls String,
) : GitWorkingTreesListEntry

internal fun buildWorkingTreesEntries(
  repositories: List<GitRepositoryModel>,
  tagNameForCommit: (repository: GitRepositoryModel, headHash: String) -> String? = { _, _ -> null },
  repositoryKind: (repository: GitRepositoryModel) -> GitRepositoryKind = { GitRepositoryKind.TOP_LEVEL },
  pendingCreations: (repository: GitRepositoryModel) -> List<GitWorktreePendingCreation> = { emptyList() },
): List<GitWorkingTreesListEntry> {
  val multiRoot = repositories.size > 1
  return repositories.sortedBy { it.root.path }
    .flatMap { buildRepositoryEntries(it, multiRoot, tagNameForCommit, repositoryKind, pendingCreations) }
}

private fun buildRepositoryEntries(
  repository: GitRepositoryModel,
  multiRoot: Boolean,
  tagNameForCommit: (repository: GitRepositoryModel, headHash: String) -> String?,
  repositoryKind: (repository: GitRepositoryModel) -> GitRepositoryKind,
  pendingCreations: (repository: GitRepositoryModel) -> List<GitWorktreePendingCreation>,
): List<GitWorkingTreesListEntry> {
  val kind = repositoryKind(repository)
  val entries = mutableListOf<GitWorkingTreesListEntry>()
  repository.state.workingTrees.sortedByDescending { it.isMain }.forEach { wt ->
    entries.add(GitWorktreeRow(
      repository = repository,
      multiRoot = multiRoot,
      gitWorkingTree = wt,
      presentableBranchName = resolvePresentableBranchName(wt) { headHash -> tagNameForCommit(repository, headHash) },
      location = FileUtil.getLocationRelativeToUserHome(wt.path.path),
      repositoryKind = kind,
    ))
  }
  pendingCreations(repository).forEach { pending ->
    entries.add(GitWorktreeCreatingRow(
      repository = repository,
      multiRoot = multiRoot,
      targetPath = pending.targetPath,
      presentableBranchName = pending.presentableBranchName,
      location = FileUtil.getLocationRelativeToUserHome(pending.targetPath.path),
    ))
  }
  return entries
}

// Branch and tag names are Git identifiers, not translatable UI text, even though the return type is @Nls.
@Suppress("HardCodedStringLiteral")
private fun resolvePresentableBranchName(worktree: GitWorkingTree, tagName: (headHash: String) -> String?): @Nls String {
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

internal fun GitWorkingTreesListEntry.tooltipText(): HtmlChunk? = when (this) {
  is GitWorktreeRow -> worktreeTooltipText(
    location, gitWorkingTree.isMain, gitWorkingTree.isLocked, gitWorkingTree.isPrunable, repositoryKind,
  )
  is GitWorktreeCreatingRow -> HtmlChunk.text(location)
}

private fun worktreeTooltipText(
  location: String, isMain: Boolean, locked: Boolean, prunable: Boolean, kind: GitRepositoryKind,
): HtmlChunk {
  val infoLine = buildList {
    add(
      if (isMain) GitBundle.message("toolwindow.working.trees.worktree.kind.main")
      else GitBundle.message("toolwindow.working.trees.worktree.kind.linked")
    )
    if (locked) add(GitBundle.message("toolwindow.working.trees.worktree.status.locked"))
    if (prunable) add(GitBundle.message("toolwindow.working.trees.worktree.status.prunable"))
  }.joinToString(" · ")
  val submoduleWarning = if (isMain && kind == GitRepositoryKind.SUBMODULE) {
    GitBundle.message("toolwindow.working.trees.submodule.relink.warning")
  }
  else null
  val lines = listOfNotNull(infoLine, submoduleWarning, location)
  return HtmlBuilder().appendWithSeparators(HtmlChunk.br(), lines.map { HtmlChunk.text(it) }).wrapWithHtmlBody()
}
