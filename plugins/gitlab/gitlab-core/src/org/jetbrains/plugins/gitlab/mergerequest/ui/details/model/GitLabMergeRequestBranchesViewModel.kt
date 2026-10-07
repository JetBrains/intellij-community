// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.dvcs.DvcsUtil
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.changesSignalFlow
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestFullDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.getSourceRemoteDescriptor
import org.jetbrains.plugins.gitlab.mergerequest.data.getSpecialRemoteBranchForHead
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestBranchUtil
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

internal class GitLabMergeRequestBranchesViewModel(
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  private val gitRemote: GitRemoteUrlCoordinates,
  private val preferredProjectAndAccount: Pair<GitLabProjectCoordinates, GitLabAccount>,
) : CodeReviewBranchesViewModel {

  private val cs: CoroutineScope = parentCs.childScope(this::class)

  override val sourceBranch: StateFlow<String> = mergeRequest.details.mapState(cs, ::getSourceBranchName)

  private fun getSourceBranchName(details: GitLabMergeRequestFullDetails): String {
    if (details.sourceProject == null) {
      if (details.diffRefs != null) return DvcsUtil.getShortHash(details.diffRefs.headSha)
      else return details.sourceBranch
    }
    if (details.targetProject == details.sourceProject) return details.sourceBranch
    val sourceProjectOwner = details.sourceProject.path.owner
    return "$sourceProjectOwner:${details.sourceBranch}"
  }

  override val isCheckedOut: StateFlow<Boolean> = gitRemote.repository.changesSignalFlow().withInitial(Unit)
    .combine(mergeRequest.details) { _, details ->
      val sourceRemote = details.getSourceRemoteDescriptor(gitRemote)
      if (sourceRemote != null) {
        GitRemoteBranchesUtil.testRemoteBranchCheckedOut(gitRemote.repository, sourceRemote, details.sourceBranch)
      }
      else {
        val specialRef = details.getSpecialRemoteBranchForHead(gitRemote.remote)
        GitRemoteBranchesUtil.testRemoteBranchCheckedOut(gitRemote.repository, specialRef)
      }
    }.stateIn(cs, SharingStarted.Eagerly, false)

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  override fun fetchAndCheckoutRemoteBranch() {
    GitLabStatistics.logMrActionExecuted(gitRemote.repository.project, GitLabStatistics.MergeRequestAction.BRANCH_CHECKOUT)
    cs.launch {
      val details = mergeRequest.details.first()
      GitLabMergeRequestBranchUtil.fetchAndCheckoutBranch(gitRemote, details)
    }
  }

  override val canCheckoutInNewWorktree: Boolean
    // A new worktree can't be created for a branch that is already checked out in the current one.
    get() = GitWorkingTreesService.isWorktreeCreationSupported(gitRemote.repository) && !isCheckedOut.value

  override fun checkoutInNewWorktree() {
    GitLabStatistics.logMrActionExecuted(gitRemote.repository.project, GitLabStatistics.MergeRequestAction.BRANCH_CHECKOUT)
    cs.launch {
      val details = mergeRequest.details.first()
      GitLabMergeRequestBranchUtil.fetchAndCheckoutBranchInNewWorktree(gitRemote,
                                                                       details,
                                                                       preferredProjectAndAccount)
    }
  }

  override val canShowInLog: Boolean = true
  override fun fetchAndShowInLog() {
    GitLabStatistics.logMrActionExecuted(gitRemote.repository.project, GitLabStatistics.MergeRequestAction.SHOW_BRANCH_IN_LOG)
    cs.launch {
      val details = mergeRequest.details.first()
      GitLabMergeRequestBranchUtil.fetchAndShowRemoteBranchInLog(gitRemote, details)
    }
  }

  override fun showBranches() {
    cs.launch {
      val details = mergeRequest.details.first()
      _showBranchesRequests.emit(CodeReviewBranches(
        getSourceBranchName(details),
        details.targetBranch,
        details.sourceProject != null
      ))
    }
  }
}
