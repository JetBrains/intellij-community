// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.GitReference
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchDialog
import git4idea.branch.GitNewBranchOptions
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus

/**
 * Checks out a remote branch into a new or an existing local branch.
 */
object GitRemoteBranchCheckoutUtil {
  @RequiresEdt
  @JvmStatic
  fun checkoutRemoteBranch(project: Project, repositories: List<GitRepository>, remoteBranchName: String) {
    val suggestedLocalName = repositories.firstNotNullOf { it.branches.findRemoteBranch(remoteBranchName)?.nameForRemoteOperations }
    checkoutRemoteBranch(project, repositories, remoteBranchName, suggestedLocalName, null)
  }

  @ApiStatus.Internal
  @RequiresEdt
  @JvmStatic
  fun checkoutRemoteBranch(
    project: Project,
    repositories: List<GitRepository>,
    remoteBranchName: String,
    suggestedLocalName: String,
    callInAwtLater: Runnable?,
  ) {
    // can have remote conflict if git-svn is used - suggested local name will be equal to selected remote
    if (GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, suggestedLocalName)) {
      askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName, callInAwtLater)
      return
    }
    val conflictingLocalBranches = repositories.mapNotNull { repo ->
      repo.branches.findLocalBranch(suggestedLocalName)?.let { repo to it }
    }.toMap()
    if (hasTrackingConflicts(conflictingLocalBranches, remoteBranchName)) {
      askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName, callInAwtLater)
    }
    else {
      GitBranchCheckoutOperation(project, repositories)
        .perform(remoteBranchName, GitNewBranchOptions(suggestedLocalName, true, true, false, repositories), callInAwtLater)
    }
  }

  @RequiresEdt
  private fun askNewBranchNameAndCheckout(
    project: Project, repositories: List<GitRepository>, remoteBranchName: String, suggestedLocalName: String, callInAwtLater: Runnable?,
  ) {
    // Do not allow name conflicts
    val options = GitNewBranchDialog(
      project,
      repositories,
      GitBundle.message("branches.checkout.s", remoteBranchName),
      suggestedLocalName,
      false,
      true
    ).showAndGetOptions() ?: return

    GitBrancher.getInstance(project).checkoutNewBranchStartingFrom(options.name,
                                                                   remoteBranchName,
                                                                   options.reset,
                                                                   options.repositories.toList(),
                                                                   callInAwtLater)
  }
}
