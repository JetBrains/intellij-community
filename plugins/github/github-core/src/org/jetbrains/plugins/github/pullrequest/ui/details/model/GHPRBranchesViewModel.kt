// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import git4idea.GitStandardRemoteBranch
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitHostingUrlUtil.getUriFromRemoteUrl
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.HostedGitRepositoryRemote
import git4idea.remote.hosting.changesSignalFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.net.URI

private val LOG = logger<GHPRBranchesViewModel>()

@ApiStatus.Experimental
class GHPRBranchesViewModel internal constructor(
  parentCs: CoroutineScope,
  private val project: Project,
  private val mapping: GHGitRepositoryMapping,
  private val detailsState: StateFlow<GHPullRequest>
) : CodeReviewBranchesViewModel {
  private val cs = parentCs.childScope(this::class)

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
      val remote = details.getHeadRemoteDescriptor(mapping.remote) ?: return@combine false
      GitRemoteBranchesUtil.isRemoteBranchCheckedOut(gitRepository, remote, details.headRefName)
    }.modelFlow(cs, thisLogger())

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  override fun fetchAndCheckoutRemoteBranch() {
    val details = detailsState.value
    cs.launch {
      fetchAndCheckoutBranch(mapping.remote, details)
      GHPRStatisticsCollector.logDetailsBranchCheckedOut(project)
    }
  }

  override val canShowInLog: Boolean = true
  override fun fetchAndShowInLog() {
    cs.launch {
      val details = detailsState.first()

      val headRemote = details.getHeadRemoteDescriptor(mapping.remote)
                         ?.let { GitRemoteBranchesUtil.findRemote(gitRepository, it) } ?: return@launch
      val baseRemote = details.getBaseRemoteDescriptor(mapping.remote)
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

    // Used as a default value for HostedGitRepositoryRemote serverUri when it's not possible to find an existing remote.
    // Path is removed, to match to every URL with the same host.
    private fun GitRemoteUrlCoordinates.toServerUri(): URI = getUriFromRemoteUrl(url)?.resolve("/")
                                                             ?: throw IllegalArgumentException("Invalid remote URL: $url")

    /**
     * Server URI should correspond to the existing remote, otherwise use the default server URI without a path.
     */
    private fun GHRepository.getRemoteDescriptor(defaultCoordinates: GitRemoteUrlCoordinates): HostedGitRepositoryRemote {
      val serverUri = defaultCoordinates.toServerUri()
      return HostedGitRepositoryRemote(owner.login, serverUri, nameWithOwner, url, sshUrl)
    }

    fun GHPullRequest.getHeadRemoteDescriptor(remoteUrlCoordinates: GitRemoteUrlCoordinates): HostedGitRepositoryRemote? =
      headRepository?.getRemoteDescriptor(remoteUrlCoordinates)

    fun GHPullRequest.getBaseRemoteDescriptor(remoteUrlCoordinates: GitRemoteUrlCoordinates): HostedGitRepositoryRemote? =
      baseRepository?.getRemoteDescriptor(remoteUrlCoordinates)

    internal suspend fun fetchAndCheckoutBranch(remoteUrlCoordinates: GitRemoteUrlCoordinates, details: GHPullRequest) {
      val baseRepository = details.baseRepository ?: run {
        LOG.warn("Can't checkout remote branch for PR ${details.number} because base repository is missing")
        return
      }
      val headRepository = details.headRepository ?: run {
        LOG.warn("Can't checkout remote branch for PR ${details.number} because head repository is missing")
        return
      }
      val isFork = headRepository != baseRepository && details.headRepository.isFork
      val localPrefix = if (isFork) "fork/${details.headRepository.owner.login}" else null
      val remoteDescriptor = headRepository.getRemoteDescriptor(remoteUrlCoordinates)
      GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(remoteUrlCoordinates.repository,
                                                         remoteDescriptor,
                                                         details.headRefName,
                                                         localPrefix)
    }
  }
}
