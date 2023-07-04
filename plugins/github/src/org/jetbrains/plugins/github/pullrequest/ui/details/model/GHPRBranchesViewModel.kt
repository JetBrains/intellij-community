// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.HostedGitRepositoryRemote
import git4idea.remote.hosting.currentBranchNameFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

internal class GHPRBranchesViewModel(
  parentCs: CoroutineScope,
  private val project: Project,
  private val mapping: GHGitRepositoryMapping,
  detailsModel: SingleValueModel<GHPullRequest>
) : CodeReviewBranchesViewModel {
  private val cs = parentCs.childScope()

  private val gitRepository = mapping.remote.repository

  private val detailsState: StateFlow<GHPullRequest> = detailsModel.asStateFlow()

  private val targetBranch: StateFlow<String> = detailsState.mapState(cs) {
    it.baseRefName
  }

  override val sourceBranch: StateFlow<String> = detailsState.mapState(cs) {
    val headRepository = it.headRepository
    if (headRepository != null && (headRepository.isFork || it.baseRefName == it.headRefName)) {
      headRepository.owner.login + ":" + it.headRefName
    }
    else {
      it.headRefName
    }
  }

  override val isCheckedOut: SharedFlow<Boolean> = gitRepository.currentBranchNameFlow().combine(sourceBranch) { currentBranch, sourceBranch ->
    currentBranch == sourceBranch
  }.modelFlow(cs, thisLogger())

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  override fun fetchAndCheckoutRemoteBranch() {
    cs.launch {
      val details = detailsState.first()
      val remoteDescriptor = details.getRemoteDescriptor() ?: return@launch
      GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(gitRepository, remoteDescriptor, details.headRefName)
    }
  }

  private fun GHPullRequest.getRemoteDescriptor(): HostedGitRepositoryRemote? = headRepository?.let {
    HostedGitRepositoryRemote(
      it.owner.login,
      mapping.repository.serverPath.toURI(),
      it.nameWithOwner,
      it.url,
      it.sshUrl
    )
  }

  override fun showBranches() {
    cs.launchNow {
      val source = sourceBranch.value
      val target = targetBranch.value
      _showBranchesRequests.emit(CodeReviewBranches(source, target))
      GHPRStatisticsCollector.logDetailsBranchesOpened(project)
    }
  }
}

private fun <T> SingleValueModel<T>.asStateFlow(): StateFlow<T> {
  val flow = MutableStateFlow(value)
  addAndInvokeListener {
    flow.value = value
  }
  return flow
}
