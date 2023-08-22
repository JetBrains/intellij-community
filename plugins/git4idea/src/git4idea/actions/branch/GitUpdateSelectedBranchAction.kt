// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import git4idea.GitBranch
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.getSelectedBranchFullPresentation
import git4idea.ui.branch.hasRemotes
import git4idea.ui.branch.isTrackingInfosExist
import git4idea.ui.branch.updateBranches
import java.util.*

//TODO: incoming/outgoing
class GitUpdateSelectedBranchAction
  : GitSingleBranchAction(GitBundle.messagePointer("branches.update")) {

  override val disabledForRemote = true

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    with(e.presentation) {
      if (!hasRemotes(project)) {
        isEnabledAndVisible = false
        return
      }

      val branchName = branch.name
      val updateMethod = GitVcsSettings.getInstance(project).updateMethod.methodName.lowercase(Locale.ROOT)
      description = GitBundle.message("action.Git.Update.Selected.description",
                                      listOf(branchName),
                                      updateMethod)

      val fetchRunning = GitFetchSupport.fetchSupport(project).isFetchRunning
      isEnabled = !fetchRunning
      if (fetchRunning) {
        description = GitBundle.message("branches.update.is.already.running")
        return
      }

      val trackingInfosExist = isTrackingInfosExist(listOf(branchName), repositories)
      isEnabled = trackingInfosExist
      if (!trackingInfosExist) {
        description = GitBundle.message("branches.tracking.branch.doesn.t.configured.for.s",
                                        getSelectedBranchFullPresentation(branchName))
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    updateBranches(project, repositories, listOf(branch.name))
  }
}