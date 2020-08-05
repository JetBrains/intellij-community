// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.branch.GitBranchUiHandlerImpl
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBranchWorker
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubSettings

class GHPRCreateBranchAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.branch.checkout.create.action"),
                                               GithubBundle.messagePointer("pull.request.branch.checkout.create.action.description"),
                                               null) {

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val repository = e.getData(GHPRActionKeys.GIT_REPOSITORY)
    val selection = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    e.presentation.isEnabled = project != null && !project.isDefault && selection != null && repository != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val repository = e.getRequiredData(GHPRActionKeys.GIT_REPOSITORY)
    val dataProvider = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)

    val pullRequestNumber = dataProvider.id.number
    val options = GitBranchUtil.getNewBranchNameFromUser(project, listOf(repository),
                                                         GithubBundle.message("pull.request.branch.checkout.create.dialog.title",
                                                                              pullRequestNumber),
                                                         generateSuggestedBranchName(pullRequestNumber, dataProvider), true) ?: return

    if (!options.checkout) {
      object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.create.task.title"), true) {
        private val git = Git.getInstance()

        override fun run(indicator: ProgressIndicator) {
          val ghPullRequest = ProgressIndicatorUtils.awaitWithCheckCanceled(dataProvider.detailsData.loadDetails(), indicator)
          val sha = ghPullRequest.headRefOid
          ProgressIndicatorUtils.awaitWithCheckCanceled(dataProvider.changesData.fetchHeadBranch(), indicator)

          indicator.text = GithubBundle.message("pull.request.branch.checkout.create.task.indicator")
          GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
            .createBranch(options.name, mapOf(repository to sha))
          if (options.setTracking) {
            trySetTrackingUpstreamBranch(git, repository, dataProvider, options.name, ghPullRequest)
          }
        }
      }.queue()
    }
    else {
      object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.task.title"), true) {
        private val git = Git.getInstance()

        override fun run(indicator: ProgressIndicator) {
          val ghPullRequest = ProgressIndicatorUtils.awaitWithCheckCanceled(dataProvider.detailsData.loadDetails(), indicator)
          val sha = ghPullRequest.headRefOid
          ProgressIndicatorUtils.awaitWithCheckCanceled(dataProvider.changesData.fetchHeadBranch(), indicator)

          indicator.text = GithubBundle.message("pull.request.branch.checkout.task.indicator")
          GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
            .checkoutNewBranchStartingFrom(options.name, sha, listOf(repository))
          if (options.setTracking) {
            trySetTrackingUpstreamBranch(git, repository, dataProvider, options.name, ghPullRequest)
          }
        }
      }.queue()
    }
  }

  private fun generateSuggestedBranchName(pullRequestNumber: Long, dataProvider: GHPRDataProvider): String =
    dataProvider.detailsData.loadedDetails?.headRefName ?: "pull/${pullRequestNumber}"

  private fun trySetTrackingUpstreamBranch(git: Git,
                                           repository: GitRepository,
                                           dataProvider: GHPRDataProvider,
                                           branchName: String,
                                           ghPullRequest: GHPullRequest) {
    val project = repository.project
    val vcsNotifier = project.service<VcsNotifier>()
    val pullRequestAuthor = ghPullRequest.author
    if (pullRequestAuthor == null) {
      vcsNotifier.notifyError(GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"),
                              GithubBundle.message("pull.request.branch.checkout.resolve.author.failed"))
      return
    }
    val httpForkUrl = dataProvider.httpForkUrl
    val sshForkUrl = dataProvider.sshForkUrl
    val forkRemote = git.findOrCreateRemote(repository, pullRequestAuthor.login, httpForkUrl, sshForkUrl)

    if (forkRemote == null) {
      var failedMessage = GithubBundle.message("pull.request.branch.checkout.resolve.remote.failed")
      if (httpForkUrl != null) {
        failedMessage += "\n$httpForkUrl"
      }
      if (sshForkUrl != null) {
        failedMessage += "\n$sshForkUrl"
      }
      vcsNotifier.notifyError(GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"), failedMessage)
      return
    }

    val forkBranchName = "${forkRemote.name}/${ghPullRequest.headRefName}"
    val fetchResult = GitFetchSupport.fetchSupport(project).fetch(repository, forkRemote, ghPullRequest.headRefName)
    if (fetchResult.showNotificationIfFailed(GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"))) {
      val setUpstream = git.setUpstream(repository, forkBranchName, branchName)
      if (!setUpstream.success()) {
        vcsNotifier.notifyError(GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"),
                                                   setUpstream.errorOutputAsJoinedString)
      }
    }
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

  private val GHPRDataProvider.httpForkUrl get() = detailsData.loadedDetails?.headRepository?.url
  private val GHPRDataProvider.sshForkUrl get() = detailsData.loadedDetails?.headRepository?.sshUrl
}