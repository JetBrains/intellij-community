// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.actions.branch

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.vcs.git.actions.GitSingleRefActions
import com.intellij.vcs.git.branch.popup.GitBranchesPopupKeys
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.rpc.GitOperationsApi
import git4idea.GitStandardLocalBranch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GitCheckoutWithUpdateAction : GitBranchActionToBeWrapped, DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ActionData.create(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val data = ActionData.create(e) ?: return
    val repositoryIds = data.repositories.map { it.repositoryId }

    GitOperationsApi.launchRequest(data.project) {
      checkoutAndUpdate(data.project.projectId(), repositoryIds, data.branch)
    }
  }

  private data class ActionData(
    val project: Project,
    val branch: GitStandardLocalBranch,
    val repositories: List<GitRepositoryModel>,
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
          !hasTrackingInfos(repositories, branch) -> null
          else -> ActionData(project, branch, repositories)
        }
      }

      private fun hasRemotes(repositories: List<GitRepositoryModel>): Boolean =
        repositories.any { it.state.remoteBranches.isNotEmpty() }

      private fun isAlreadyCheckedOut(repositories: List<GitRepositoryModel>, branch: GitStandardLocalBranch): Boolean =
        repositories.all { it.state.isCurrentRef(branch) }

      private fun hasTrackingInfos(repositories: List<GitRepositoryModel>, branch: GitStandardLocalBranch): Boolean =
        repositories.any { it.state.getTrackingInfo(branch) != null }
    }
  }
}