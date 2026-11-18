// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.openapi.components.service
import git4idea.GitRemoteBranch
import git4idea.push.GitSpecialRefRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitRemoteBranchesUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestFullDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.getSourceRemoteDescriptor
import org.jetbrains.plugins.gitlab.mergerequest.data.getSpecialRemoteBranchForHead
import org.jetbrains.plugins.gitlab.mergerequest.data.getTargetRemoteDescriptor
import org.jetbrains.plugins.gitlab.mergerequest.data.isFork
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel

object GitLabMergeRequestBranchUtil {
  private const val FORK_BRANCH_PREFIX = "fork"
  private const val WORKTREE_FROM_REVIEW_PLACE = "review.details.branch.popup"

  private suspend fun findSourceRemoteBranch(
    gitRemote: GitRemoteUrlCoordinates,
    details: GitLabMergeRequestFullDetails,
  ): GitRemoteBranch? {
    val sourceRemoteDescriptor = details.getSourceRemoteDescriptor(gitRemote)

    if (sourceRemoteDescriptor != null) {
      // Public fork / regular branch
      return GitRemoteBranchesUtil.findOrCreateRemoteBranch(gitRemote.repository, sourceRemoteDescriptor, details.sourceBranch)
    } else {
      // Private/deleted fork, can still fetch using special MR head ref
      val targetRemoteDescriptor = details.getTargetRemoteDescriptor(gitRemote)
      val targetRemote = GitRemoteBranchesUtil.findOrCreateRemote(gitRemote.repository, targetRemoteDescriptor) ?: return null
      return details.getSpecialRemoteBranchForHead(targetRemote)
    }
  }

  private suspend fun findTargetRemoteBranch(
    gitRemote: GitRemoteUrlCoordinates,
    details: GitLabMergeRequestFullDetails,
  ): GitRemoteBranch? {
    val targetRemoteDescriptor = details.getTargetRemoteDescriptor(gitRemote)

    return GitRemoteBranchesUtil.findOrCreateRemoteBranch(gitRemote.repository, targetRemoteDescriptor, details.targetBranch)
  }

  private fun getLocalBranchPrefix(details: GitLabMergeRequestFullDetails): String? =
    if (details.isFork()) {
      details.sourceProject?.let { "${FORK_BRANCH_PREFIX}/${it.path.owner}" }
      ?: "${FORK_BRANCH_PREFIX}/${details.author.username}"
    } else null

  suspend fun fetchAndCheckoutBranch(
    gitRemote: GitRemoteUrlCoordinates,
    details: GitLabMergeRequestFullDetails,
  ) {
    val localPrefix = getLocalBranchPrefix(details)
    val remoteBranch = findSourceRemoteBranch(gitRemote, details) ?: return
    GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(gitRemote.repository, remoteBranch, localPrefix)
  }

  /**
   * Fetches the merge request source branch and opens it in a new worktree project.
   * The new project then connects to [preferredProjectAndAccount] and shows the merge request details and diff.
   */
  internal suspend fun fetchAndCheckoutBranchInNewWorktree(
    gitRemote: GitRemoteUrlCoordinates,
    details: GitLabMergeRequestFullDetails,
    preferredProjectAndAccount: Pair<GitLabProjectCoordinates, GitLabAccount>,
  ) {
    val remoteBranch = findSourceRemoteBranch(gitRemote, details) ?: return
    val localPrefix = getLocalBranchPrefix(details)
    // A special ref name is not a valid local branch name, so name the local branch after the source branch.
    val localBranchName = if (remoteBranch is GitSpecialRefRemoteBranch) {
      listOfNotNull(localPrefix, details.sourceBranch).joinToString("/")
    }
    else {
      localPrefix?.let { "$it/${remoteBranch.nameForRemoteOperations}" }
    }
    val iid = details.iid
    val worktreeName = "${gitRemote.repository.root.name}_MR_$iid"
    val parentDir = withContext(Dispatchers.IO) {
      GitRemoteBranchesUtil.getReviewWorktreesParentDir(gitRemote.repository.project)
    }
    GitRemoteBranchesUtil.fetchAndCheckoutInNewWorktree(gitRemote.repository,
                                                        remoteBranch,
                                                        parentDir,
                                                        worktreeName,
                                                        WORKTREE_FROM_REVIEW_PLACE,
                                                        localBranchName) { worktreeProject ->
      worktreeProject.service<GitLabProjectViewModel>().activateAndAwaitProject(preferredProjectAndAccount) {
        openMergeRequestInfoAndDiff(iid)
      }
    }
  }

  suspend fun fetchAndShowRemoteBranchInLog(
    gitRemote: GitRemoteUrlCoordinates,
    details: GitLabMergeRequestFullDetails,
  ) {
    val sourceRemoteBranch = findSourceRemoteBranch(gitRemote, details) ?: return
    val targetRemoteBranch = findTargetRemoteBranch(gitRemote, details)

    GitRemoteBranchesUtil.fetchAndShowRemoteBranchInLog(gitRemote.repository, sourceRemoteBranch, targetRemoteBranch)
  }
}
