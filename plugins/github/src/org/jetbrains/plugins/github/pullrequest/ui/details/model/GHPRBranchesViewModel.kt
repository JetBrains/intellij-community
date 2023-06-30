// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.util.childScope
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.currentBranchNameFlow
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubSettings

internal class GHPRBranchesViewModel(
  parentCs: CoroutineScope,
  private val project: Project,
  private val repository: GitRepository,
  detailsModel: SingleValueModel<GHPullRequest>
) : CodeReviewBranchesViewModel {
  private val cs = parentCs.childScope()

  private val git: Git = Git.getInstance()
  private val vcsNotifier: VcsNotifier = project.service<VcsNotifier>()

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

  override val isCheckedOut: SharedFlow<Boolean> = repository.currentBranchNameFlow().combine(sourceBranch) { currentBranch, sourceBranch ->
    currentBranch == sourceBranch
  }.modelFlow(cs, thisLogger())

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  override fun fetchAndCheckoutRemoteBranch() {
    val pullRequest = detailsState.value
    val headBranch = pullRequest.headRefName
    val httpForkUrl = pullRequest.headRepository?.url
    val sshForkUrl = pullRequest.headRepository?.sshUrl
    val pullRequestAuthor = pullRequest.author
    if (pullRequestAuthor == null) {
      vcsNotifier.notifyError(
        GithubNotificationIdsHolder.PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
        GithubBundle.message("pull.request.branch.checkout.remote.cannot.find"),
        GithubBundle.message("pull.request.branch.checkout.resolve.author.failed")
      )
      return
    }

    object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.task.indicator"), true) {
      override fun run(indicator: ProgressIndicator) {
        val headRemote = git.findOrCreateRemote(repository, pullRequestAuthor.login, httpForkUrl, sshForkUrl)
        if (headRemote == null) {
          notifyRemoteError(vcsNotifier, httpForkUrl, sshForkUrl)
          return
        }

        val fetchResult = GitFetchSupport.fetchSupport(project).fetch(repository, headRemote, headBranch)
        if (fetchResult.showNotificationIfFailed(GitBundle.message("branches.update.failed"))) {
          val branch = "${headRemote.name}/${headBranch}"
          invokeLater {
            GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction.checkoutRemoteBranch(project, listOf(repository), branch)
            GHPRStatisticsCollector.logDetailsBranchCheckedOut(project)
          }
        }
      }
    }.queue()
  }

  private fun notifyRemoteError(vcsNotifier: VcsNotifier, httpForkUrl: @NlsSafe String?, sshForkUrl: @NlsSafe String?) {
    var failedMessage = GithubBundle.message("pull.request.branch.checkout.resolve.remote.failed")
    if (httpForkUrl != null) {
      failedMessage += "\n$httpForkUrl"
    }
    if (sshForkUrl != null) {
      failedMessage += "\n$sshForkUrl"
    }
    vcsNotifier.notifyError(
      GithubNotificationIdsHolder.PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
      GithubBundle.message("pull.request.branch.checkout.remote.cannot.find"),
      failedMessage
    )
  }

  private fun Git.findOrCreateRemote(repository: GitRepository, remoteName: String, httpUrl: String?, sshUrl: String?): GitRemote? {
    val existingRemote = GithubGitHelper.getInstance().findRemote(repository, httpUrl, sshUrl)
    if (existingRemote != null) return existingRemote

    val useSshUrl = GithubSettings.getInstance().isCloneGitUsingSsh
    val sshOrHttpUrl = if (useSshUrl) sshUrl else httpUrl

    if (sshOrHttpUrl != null && repository.remotes.any { it.name == remoteName }) {
      return createRemote(repository, "pull_$remoteName", sshOrHttpUrl)
    }

    return when {
      useSshUrl && sshUrl != null -> createRemote(repository, remoteName, sshUrl)
      !useSshUrl && httpUrl != null -> createRemote(repository, remoteName, httpUrl)
      sshUrl != null -> createRemote(repository, remoteName, sshUrl)
      httpUrl != null -> createRemote(repository, remoteName, httpUrl)
      else -> null
    }
  }

  private fun Git.createRemote(repository: GitRepository, remoteName: String, url: String): GitRemote? =
    with(repository) {
      addRemote(this, remoteName, url)
      update()
      remotes.find { it.name == remoteName }
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
