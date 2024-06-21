// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.GitStandardRemoteBranch
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.HostedGitRepositoryRemote
import git4idea.remote.hosting.changesSignalFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

@ApiStatus.Experimental
class GHPRBranchesViewModel internal constructor(
  parentCs: CoroutineScope,
  private val project: Project,
  private val mapping: GHGitRepositoryMapping,
  private val detailsState: StateFlow<GHPullRequest>
) : CodeReviewBranchesViewModel {
  private val cs = parentCs.childScope()

  private val gitRepository = mapping.remote.repository

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

  override val isCheckedOut: SharedFlow<Boolean> = gitRepository.changesSignalFlow().withInitial(Unit)
    .combine(detailsState) { _, details ->
      val remote = details.getHeadRemoteDescriptor(mapping.repository.serverPath) ?: return@combine false
      GitRemoteBranchesUtil.isRemoteBranchCheckedOut(gitRepository, remote, details.headRefName)
    }.modelFlow(cs, thisLogger())

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  override fun fetchAndCheckoutRemoteBranch() {
    cs.launch {
      val details = detailsState.first()
      val remoteDescriptor = details.getHeadRemoteDescriptor(mapping.repository.serverPath) ?: return@launch
      val localPrefix = if (details.headRepository?.isFork == true) "fork/${details.headRepository.owner.login}" else null
      GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(gitRepository, remoteDescriptor, details.headRefName, localPrefix)
    }
  }

  override val canShowInLog: Boolean = true
  override fun fetchAndShowInLog() {
    cs.launch {
      val details = detailsState.first()

      val headRemote = details.getHeadRemoteDescriptor(mapping.repository.serverPath)
                         ?.let { GitRemoteBranchesUtil.findRemote(gitRepository, it) } ?: return@launch
      val baseRemote = details.getBaseRemoteDescriptor(mapping.repository.serverPath)
                         ?.let { GitRemoteBranchesUtil.findRemote(gitRepository, it) } ?: return@launch

      val headBranch = GitStandardRemoteBranch(headRemote, details.headRefName)
      val baseBranch = GitStandardRemoteBranch(baseRemote, details.baseRefName)

      GitRemoteBranchesUtil.fetchAndShowRemoteBranchInLog(gitRepository, headBranch, baseBranch)
    }
  }

  override fun showBranches() {
    cs.launchNow {
      val source = sourceBranch.value
      val target = targetBranch.value
      _showBranchesRequests.emit(CodeReviewBranches(source, target))
      GHPRStatisticsCollector.logDetailsBranchesOpened(project)
    }
  }

  companion object {
    fun GHRepository.getRemoteDescriptor(server: GithubServerPath): HostedGitRepositoryRemote =
      HostedGitRepositoryRemote(owner.login, server.toURI(), nameWithOwner, url, sshUrl)

    fun GHPullRequest.getHeadRemoteDescriptor(server: GithubServerPath): HostedGitRepositoryRemote? =
      headRepository?.getRemoteDescriptor(server)

    fun GHPullRequest.getBaseRemoteDescriptor(server: GithubServerPath): HostedGitRepositoryRemote? =
      baseRepository?.getRemoteDescriptor(server)
  }
}
