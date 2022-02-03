// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.containers.ContainerUtil
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchDialog
import git4idea.branch.GitNewBranchOptions
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.*
import git4idea.ui.branch.GitCheckoutAndRebaseRemoteBranchWorkflow
import git4idea.ui.branch.hasTrackingConflicts
import java.util.*

class GitCheckoutWithRebaseAction
  : GitSingleBranchAction() {

  override val disabledForCurrent = true

  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    val description = GitBundle.message("branches.checkout.and.rebase.onto.in.one.step",
                                        getSelectedBranchFullPresentation(branch.name),
                                        getCurrentBranchFullPresentation(project, repositories),
                                        branch.name)
    val presentation = e.presentation
    presentation.text = GitBundle.message("branches.checkout.and.rebase.onto.branch",
                                          getCurrentBranchTruncatedPresentation(project, repositories))
    presentation.description = description
    addTooltipText(presentation, description)
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    if (branch is GitRemoteBranch) {
      checkoutAndRebaseRemote(project, repositories, branch)
    }
    else {
      GitBrancher.getInstance(project).rebaseOnCurrent(repositories, branch.name)
    }
  }

  private fun checkoutAndRebaseRemote(project: Project, repositories: List<GitRepository>, branch: GitRemoteBranch) {
    val suggestedLocalName = branch.nameForRemoteOperations

    var newBranchOptions: GitNewBranchOptions? = GitNewBranchOptions(suggestedLocalName, false, true)
    // can have remote conflict if git-svn is used  - suggested local name will be equal to selected remote
    if (GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(branch.name, suggestedLocalName)) {
      newBranchOptions = askBranchName(project, repositories, branch, suggestedLocalName)
    }
    if (newBranchOptions == null) return

    val localName = newBranchOptions.name
    val conflictingLocalBranches = ContainerUtil.map2MapNotNull(repositories) { r: GitRepository ->
      val local = r.branches.findLocalBranch(localName)
      if (local != null) Pair.create(r, local) else null
    }
    if (hasTrackingConflicts(conflictingLocalBranches, branch.name)) {
      newBranchOptions = askBranchName(project, repositories, branch, localName)
    }
    if (newBranchOptions == null) return

    val workflow = GitCheckoutAndRebaseRemoteBranchWorkflow(project, repositories)
    workflow.execute(branch.nameForLocalOperations, newBranchOptions)
  }

  private fun askBranchName(project: Project, repositories: List<GitRepository>, branch: GitRemoteBranch, suggestedLocalName: String)
    : GitNewBranchOptions? {
    return GitNewBranchDialog(project, repositories, GitBundle.message("branches.checkout.s", branch.name), suggestedLocalName,
                              false, true)
      .showAndGetOptions()
  }
}