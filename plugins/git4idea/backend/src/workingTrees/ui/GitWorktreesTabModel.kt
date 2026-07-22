// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.getTagsForCommit
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.GitWorktreeSupportStatus
import org.jetbrains.annotations.Nls

/**
 * Backing model for the Worktrees tab: exposes the tab's support status, the repository the tab-scoped
 * actions operate on, and the rows to render. Introduced to keep the panel, list model and renderer free
 * of support-status and worktree-loading logic.
 */
internal class GitWorktreesTabModel(private val project: Project) {
  fun supportStatus(): GitWorktreeSupportStatus = GitWorkingTreesService.getWorktreeSupportStatus(project)

  fun currentRepository(): GitRepository? =
    (supportStatus() as? GitWorktreeSupportStatus.SingleRepository)?.repository

  fun buildRows(): List<GitWorktreeRow> {
    val repository = currentRepository() ?: return emptyList()
    return repository.workingTreeHolder.getWorkingTrees()
      .sortedByDescending { it.isMain }
      .map { wt -> GitWorktreeRow(wt, presentableBranchName(wt, repository), FileUtil.getLocationRelativeToUserHome(wt.path.path)) }
  }

  @Nls
  private fun presentableBranchName(worktree: GitWorkingTree, repository: GitRepository): String {
    worktree.currentBranch?.let { return it.name }
    worktree.headHash?.let { headHash ->
      repository.tagsHolder.getTagsForCommit(headHash).firstOrNull()?.let { return it.name }
    }
    return GitBundle.message("toolwindow.working.trees.tab.detached.working.tree.branch.text")
  }
}
