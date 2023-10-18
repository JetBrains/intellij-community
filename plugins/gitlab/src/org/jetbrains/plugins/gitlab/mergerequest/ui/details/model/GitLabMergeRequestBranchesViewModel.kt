// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.childScope
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.changesSignalFlow
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestFullDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.getRemoteDescriptor
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

internal class GitLabMergeRequestBranchesViewModel(
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  private val mapping: GitLabProjectMapping
) : CodeReviewBranchesViewModel {
  private val gitRepository: GitRepository = mapping.remote.repository

  private val cs: CoroutineScope = parentCs.childScope()

  override val sourceBranch: StateFlow<String> = mergeRequest.details.mapState(cs, ::getSourceBranchName)

  private fun getSourceBranchName(details: GitLabMergeRequestFullDetails): String {
    if (details.sourceProject == null) return ""
    if (details.targetProject == details.sourceProject) return details.sourceBranch
    val sourceProjectOwner = details.sourceProject.ownerPath
    return "$sourceProjectOwner:${details.sourceBranch}"
  }

  override val isCheckedOut: SharedFlow<Boolean> = gitRepository.changesSignalFlow().withInitial(Unit)
    .combine(mergeRequest.details) { _, details ->
      val remote = details.getRemoteDescriptor(mapping.repository.serverPath) ?: return@combine false
      GitRemoteBranchesUtil.isRemoteBranchCheckedOut(gitRepository, remote, details.sourceBranch)
    }.modelFlow(cs, thisLogger())

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  override fun fetchAndCheckoutRemoteBranch() {
    cs.launch {
      val details = mergeRequest.details.first()
      val remoteDescriptor = details.getRemoteDescriptor(mapping.repository.serverPath) ?: return@launch
      val localPrefix = if(details.sourceProject?.fullPath != details.targetProject.fullPath) {
        "fork/${remoteDescriptor.name}"
      } else {
        null
      }
      GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(gitRepository, remoteDescriptor, details.sourceBranch, localPrefix)
    }
  }

  override fun showBranches() {
    cs.launch {
      val details = mergeRequest.details.first()
      _showBranchesRequests.emit(CodeReviewBranches(details.sourceBranch, details.targetBranch))
    }
  }
}
