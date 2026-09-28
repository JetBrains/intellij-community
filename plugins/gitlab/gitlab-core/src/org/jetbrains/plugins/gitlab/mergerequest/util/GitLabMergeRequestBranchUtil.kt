// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.openapi.components.service
import git4idea.GitRemoteBranch
import git4idea.push.GitSpecialRefRemoteBranch
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
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
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails,
  ): GitRemoteBranch? {
    val sourceRemoteDescriptor = details.getSourceRemoteDescriptor(serverPath)

    if (sourceRemoteDescriptor != null) {
      // Public fork / regular branch
      return GitRemoteBranchesUtil.findOrCreateRemoteBranch(gitRepository, sourceRemoteDescriptor, details.sourceBranch)
    } else {
      // Private/deleted fork, can still fetch using special MR head ref
      val targetRemoteDescriptor = details.getTargetRemoteDescriptor(serverPath)
      val targetRemote = GitRemoteBranchesUtil.findOrCreateRemote(gitRepository, targetRemoteDescriptor) ?: return null
      return details.getSpecialRemoteBranchForHead(targetRemote)
    }
  }

  private suspend fun findTargetRemoteBranch(
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails,
  ): GitRemoteBranch? {
    val targetRemoteDescriptor = details.getTargetRemoteDescriptor(serverPath)

    return GitRemoteBranchesUtil.findOrCreateRemoteBranch(gitRepository, targetRemoteDescriptor, details.targetBranch)
  }

  private fun getLocalBranchPrefix(details: GitLabMergeRequestFullDetails): String? =
    if (details.isFork()) {
      if (details.sourceProject != null) "${FORK_BRANCH_PREFIX}/${details.sourceProject.path.owner}"
      else "${FORK_BRANCH_PREFIX}/${details.author.username}"
    } else null

  suspend fun fetchAndCheckoutBranch(
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails,
  ) {
    val localPrefix = getLocalBranchPrefix(details)
    val remoteBranch = findSourceRemoteBranch(gitRepository, serverPath, details) ?: return
    GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(gitRepository, remoteBranch, localPrefix)
  }

  /**
   * Fetches the merge request source branch and opens it in a new worktree project.
   * The new project then connects to [preferredProjectAndAccount] and shows the merge request details and diff.
   */
  internal suspend fun fetchAndCheckoutBranchInNewWorktree(
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails,
    preferredProjectAndAccount: Pair<GitLabProjectCoordinates, GitLabAccount>,
  ) {
    val remoteBranch = findSourceRemoteBranch(gitRepository, serverPath, details) ?: return
    val localPrefix = getLocalBranchPrefix(details)
    // A special ref name is not a valid local branch name, so name the local branch after the source branch.
    val localBranchName = if (remoteBranch is GitSpecialRefRemoteBranch) {
      listOfNotNull(localPrefix, details.sourceBranch).joinToString("/")
    }
    else {
      localPrefix?.let { "$it/${remoteBranch.nameForRemoteOperations}" }
    }
    val iid = details.iid
    val worktreeName = "${gitRepository.root.name}_MR_$iid"
    val parentDir = withContext(Dispatchers.IO) {
      GitRemoteBranchesUtil.getReviewWorktreesParentDir(gitRepository.project)
    }
    GitRemoteBranchesUtil.fetchAndCheckoutInNewWorktree(gitRepository,
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
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails,
  ) {
    val sourceRemoteBranch = findSourceRemoteBranch(gitRepository, serverPath, details) ?: return
    val targetRemoteBranch = findTargetRemoteBranch(gitRepository, serverPath, details)

    GitRemoteBranchesUtil.fetchAndShowRemoteBranchInLog(gitRepository, sourceRemoteBranch, targetRemoteBranch)
  }
}
