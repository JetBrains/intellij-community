// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import git4idea.GitRemoteBranch
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestFullDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.getSourceRemoteDescriptor
import org.jetbrains.plugins.gitlab.mergerequest.data.getSpecialRemoteBranchForHead
import org.jetbrains.plugins.gitlab.mergerequest.data.getTargetRemoteDescriptor
import org.jetbrains.plugins.gitlab.mergerequest.data.isFork

object GitLabMergeRequestBranchUtil {
  private const val FORK_BRANCH_PREFIX = "fork"

  private suspend fun findSourceRemoteBranch(
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails
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
    details: GitLabMergeRequestFullDetails
  ): GitRemoteBranch? {
    val targetRemoteDescriptor = details.getTargetRemoteDescriptor(serverPath)

    return GitRemoteBranchesUtil.findOrCreateRemoteBranch(gitRepository, targetRemoteDescriptor, details.targetBranch)
  }

  suspend fun fetchAndCheckoutBranch(
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails
  ) {
    val localPrefix = if (details.isFork()) {
      if (details.sourceProject != null) "${FORK_BRANCH_PREFIX}/${details.sourceProject.path.owner}"
      else "${FORK_BRANCH_PREFIX}/${details.author.username}"
    } else null

    val remoteBranch = findSourceRemoteBranch(gitRepository, serverPath, details) ?: return
    GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(gitRepository, remoteBranch, localPrefix)
  }

  suspend fun fetchAndShowRemoteBranchInLog(
    gitRepository: GitRepository,
    serverPath: GitLabServerPath,
    details: GitLabMergeRequestFullDetails
  ) {
    val sourceRemoteBranch = findSourceRemoteBranch(gitRepository, serverPath, details) ?: return
    val targetRemoteBranch = findTargetRemoteBranch(gitRepository, serverPath, details)

    GitRemoteBranchesUtil.fetchAndShowRemoteBranchInLog(gitRepository, sourceRemoteBranch, targetRemoteBranch)
  }
}