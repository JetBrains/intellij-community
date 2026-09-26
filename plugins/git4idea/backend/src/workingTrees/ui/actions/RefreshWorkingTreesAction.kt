// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.GitWorktreeSupportStatus

internal class RefreshWorkingTreesAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = repositoriesToRefresh(e).isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    // Refresh the whole tab: reload every repository's worktrees, regardless of the current selection.
    repositoriesToRefresh(e).forEach { it.workingTreeHolder.scheduleReload() }
  }

  private fun repositoriesToRefresh(e: AnActionEvent): List<GitRepository> {
    return when (val status = GitWorkingTreesService.getWorktreeSupportStatus(e.project)) {
      is GitWorktreeSupportStatus.SingleRepository -> listOf(status.repository)
      is GitWorktreeSupportStatus.MultipleRepository -> status.repositories
      GitWorktreeSupportStatus.Unsupported -> emptyList()
    }
  }
}
