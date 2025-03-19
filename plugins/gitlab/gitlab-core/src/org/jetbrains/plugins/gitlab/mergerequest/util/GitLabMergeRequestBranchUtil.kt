// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import git4idea.GitRemoteBranch
import git4idea.remote.hosting.GitRemoteBranchesUtil
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

object GitLabMergeRequestBranchUtil {
  private const val FORK_BRANCH_PREFIX = "fork"

  private suspend fun findSourceRemoteBranch(mapping: GitLabProjectMapping, details: GitLabMergeRequestFullDetails): GitRemoteBranch? {
    val sourceRemoteDescriptor = details.getSourceRemoteDescriptor(mapping.repository.serverPath)

    if (sourceRemoteDescriptor != null) {
      // Public fork / regular branch
      return GitRemoteBranchesUtil.findOrCreateRemoteBranch(mapping.gitRepository, sourceRemoteDescriptor, details.sourceBranch)
    } else {
      // Private/deleted fork, can still fetch using special MR head ref
      val targetRemoteDescriptor = details.getTargetRemoteDescriptor(mapping.repository.serverPath)
      val targetRemote = GitRemoteBranchesUtil.findOrCreateRemote(mapping.gitRepository, targetRemoteDescriptor) ?: return null
      return details.getSpecialRemoteBranchForHead(targetRemote)
    }
  }

  private suspend fun findTargetRemoteBranch(mapping: GitLabProjectMapping, details: GitLabMergeRequestFullDetails): GitRemoteBranch? {
    val targetRemoteDescriptor = details.getTargetRemoteDescriptor(mapping.repository.serverPath)

    return GitRemoteBranchesUtil.findOrCreateRemoteBranch(mapping.gitRepository, targetRemoteDescriptor, details.targetBranch)
  }

  suspend fun fetchAndCheckoutBranch(mapping: GitLabProjectMapping, details: GitLabMergeRequestFullDetails) {
    val localPrefix = if (details.isFork()) {
      if (details.sourceProject != null) "${FORK_BRANCH_PREFIX}/${details.sourceProject.ownerPath}"
      else "${FORK_BRANCH_PREFIX}/${details.author.username}"
    } else null

    val remoteBranch = findSourceRemoteBranch(mapping, details) ?: return
    GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(mapping.gitRepository, remoteBranch, localPrefix)
  }

  suspend fun fetchAndShowRemoteBranchInLog(mapping: GitLabProjectMapping, details: GitLabMergeRequestFullDetails) {
    val sourceRemoteBranch = findSourceRemoteBranch(mapping, details) ?: return
    val targetRemoteBranch = findTargetRemoteBranch(mapping, details)

    GitRemoteBranchesUtil.fetchAndShowRemoteBranchInLog(mapping.gitRepository, sourceRemoteBranch, targetRemoteBranch)
  }
}