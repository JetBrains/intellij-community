// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranches
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.util.childScope
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.ui.branch.GitBranchPopupActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubSettings

internal class GHPRBranchesViewModel(
  parentCs: CoroutineScope,
  private val project: Project,
  private val branchesModel: GHPRBranchesModel,
  private val detailsDataProvider: GHPRDetailsDataProvider
) : CodeReviewBranchesViewModel {
  private val cs = parentCs.childScope()

  private val git: Git = Git.getInstance()
  private val vcsNotifier: VcsNotifier = project.service<VcsNotifier>()

  private val _targetBranch: MutableStateFlow<String> = MutableStateFlow(branchesModel.baseBranch)

  private val _sourceBranch: MutableStateFlow<String> = MutableStateFlow(branchesModel.headBranch)
  override val sourceBranch: StateFlow<String> = _sourceBranch.asStateFlow()

  override val isCheckedOut: SharedFlow<Boolean> = callbackFlow {
    val cs = this
    send(isBranchCheckedOut(branchesModel.localRepository, sourceBranch.value))

    project.messageBus
      .connect(cs)
      .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        trySend(isBranchCheckedOut(it, sourceBranch.value))
      })

    _sourceBranch.collect { sourceBranch ->
      send(isBranchCheckedOut(branchesModel.localRepository, sourceBranch))
    }
  }.modelFlow(cs, thisLogger())

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  init {
    branchesModel.addAndInvokeChangeListener {
      _targetBranch.value = branchesModel.baseBranch
      _sourceBranch.value = branchesModel.headBranch
    }
  }

  override fun fetchAndCheckoutRemoteBranch() {
    val pullRequest = ProgressIndicatorUtils.awaitWithCheckCanceled(detailsDataProvider.loadDetails(), EmptyProgressIndicator())
    if (pullRequest.author == null) {
      vcsNotifier.notifyError(
        GithubNotificationIdsHolder.PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
        GithubBundle.message("pull.request.branch.checkout.remote.cannot.find"),
        GithubBundle.message("pull.request.branch.checkout.resolve.author.failed")
      )
      return
    }

    object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.task.indicator"), true) {
      override fun run(indicator: ProgressIndicator) {
        val headBranch = pullRequest.headRefName
        val httpForkUrl = pullRequest.headRepository?.url
        val sshForkUrl = pullRequest.headRepository?.sshUrl
        val pullRequestAuthor = pullRequest.author
        val repository = branchesModel.localRepository

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

  private fun isBranchCheckedOut(repository: GitRepository, sourceBranch: String): Boolean {
    val currentBranchName = repository.currentBranchName
    return currentBranchName == sourceBranch
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
      val source = _sourceBranch.value
      val target = _targetBranch.value
      _showBranchesRequests.emit(CodeReviewBranches(source, target))
      GHPRStatisticsCollector.logDetailsBranchesOpened(project)
    }
  }
}