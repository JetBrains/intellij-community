// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.actions.branch

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.vcs.git.actions.GitSingleRefActions
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.rpc.GitOperationsApi
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GitCheckoutWithUpdateAction : GitBranchActionToBeWrapped, DumbAwareAction(),
                                    ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val data = ActionData.create(e)
    with(e.presentation) {
      if (data == null) {
        isEnabledAndVisible = false
        return
      }

      val localBranch = data.branch
      val remoteBranch = data.remoteBranches.firstOrNull()
      isEnabledAndVisible = true
      isEnabled = remoteBranch != null
      val descriptionText = if (remoteBranch == null) {
        GitBundle.message("branches.tracking.branch.doesn.t.configured.for.s",
                          "'${data.branch.name}'")
      }
      else {
        GitBundle.message("action.Git.Checkout.Update.description.with.branch.names", "'${localBranch.name}'", "'${remoteBranch.nameForLocalOperations}'")
      }

      description = descriptionText
      putClientProperty(ActionUtil.TOOLTIP_TEXT, description)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val data = ActionData.create(e) ?: return
    if (data.remoteBranches.isEmpty()) return
    val repositoryIds = data.repositories.map { it.repositoryId }

    GitOperationsApi.launchRequest(data.project) {
      checkoutAndUpdate(data.project.projectId(), repositoryIds, data.branch)
    }
  }

  private data class ActionData(
    val project: Project,
    val branch: GitStandardLocalBranch,
    val repositories: List<GitRepositoryModel>,
    val remoteBranches: List<GitStandardRemoteBranch>,
  ) {
    companion object {
      fun create(e: AnActionEvent): ActionData? {
        val project = e.project ?: return null
        val branch = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY) as? GitStandardLocalBranch ?: return null

        val repositories = e.getData(GitBranchesPopupKeys.AFFECTED_REPOSITORIES)
                             ?.takeIf { it.isNotEmpty() } ?: return null

        return when {
          !hasRemotes(repositories) -> null
          isAlreadyCheckedOut(repositories, branch) -> null
          else -> ActionData(project, branch, repositories, getRemoteBranches(repositories, branch))
        }
      }

      private fun hasRemotes(repositories: List<GitRepositoryModel>): Boolean =
        repositories.any { it.state.remoteBranches.isNotEmpty() }

      private fun isAlreadyCheckedOut(repositories: List<GitRepositoryModel>, branch: GitStandardLocalBranch): Boolean {
        if (repositories.any {
            GitWorkingTreesUtil.getWorkingTreeWithRef(branch, it, true) != null
          }) {
          return true
        }
        return repositories.all { it.state.isCurrentRef(branch) }
      }

      private fun getRemoteBranches(repositories: List<GitRepositoryModel>, branch: GitStandardLocalBranch): List<GitStandardRemoteBranch> =
        repositories.mapNotNull { it.state.getTrackingInfo(branch) }
    }
  }
}