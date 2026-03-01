// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitBranchPopupActions.getSelectedBranchFullPresentation
import git4idea.ui.branch.hasAnyRemotes
import git4idea.ui.branch.isTrackingInfosExist
import git4idea.ui.branch.updateBranches
import java.util.Locale

class GitUpdateSelectedBranchAction
  : GitSingleBranchAction(GitBundle.messagePointer("branches.update")) {

  override val disabledForRemote = true

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    if (!hasAnyRemotes(repositories)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val workingTreeWithBranch = getWorkingTreeWithRef(branch, repositories, skipCurrentWorkingTree = true)

    val (enabled, description) = when {
      workingTreeWithBranch != null -> {
        false to GitBundle.message("branches.update.checked.out.in.worktree", workingTreeWithBranch.path.presentableUrl)
      }
      GitFetchSupport.fetchSupport(project).isFetchRunning -> {
        false to GitBundle.message("branches.update.is.already.running")
      }
      !isTrackingInfosExist(listOf(branch.name), repositories) -> {
        false to GitBundle.message("branches.tracking.branch.doesn.t.configured.for.s", getSelectedBranchFullPresentation(branch.name))
      }
      else -> {
        val updateMethod = GitVcsSettings.getInstance(project).updateMethod.methodName.lowercase(Locale.ROOT)
        true to GitBundle.message("action.Git.Update.Selected.description", listOf(branch.name).size, updateMethod)
      }
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = enabled
    e.presentation.description = description

    GitBranchPopupActions.addTooltipText(e.presentation, description)
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    updateBranches(project, repositories, listOf(branch.name))
  }
}