// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
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
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubSettings

class GHPRCreateBranchAction : DumbAwareAction(GithubBundle.messagePointer("pull.request.branch.checkout.create.action"),
                                               GithubBundle.messagePointer("pull.request.branch.checkout.create.action.description"),
                                               null) {

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val repository = e.getData(GHPRActionKeys.GIT_REPOSITORY)
    val selection = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)
    if (repository != null) {
      val loadedDetails = selection?.detailsData?.loadedDetails
      val headRefName = loadedDetails?.headRefName
      val httpUrl = loadedDetails?.headRepository?.url
      val sshUrl = loadedDetails?.headRepository?.sshUrl
      val isFork = loadedDetails?.headRepository?.isFork ?: false
      val remote = GithubGitHelper.getInstance().findRemote(repository, httpUrl, sshUrl)
      if (remote != null) {
        val localBranch = GithubGitHelper.getInstance().findLocalBranch(repository, remote, isFork, headRefName)
        if (repository.currentBranchName == localBranch) {
          e.presentation.isEnabled = false
          return
        }
      }
    }
    e.presentation.isEnabled = project != null && !project.isDefault && selection != null && repository != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val repository = e.getRequiredData(GHPRActionKeys.GIT_REPOSITORY)
    val dataProvider = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)

    val pullRequestNumber = dataProvider.id.number
    checkoutOrCreateNew(project, repository, pullRequestNumber, dataProvider)
  }

  private fun checkoutOrCreateNew(project: Project, repository: GitRepository, pullRequestNumber: Long, dataProvider: GHPRDataProvider) {
    val httpForkUrl = dataProvider.httpForkUrl
    val sshForkUrl = dataProvider.sshForkUrl
    val possibleBranchName = dataProvider.detailsData.loadedDetails?.headRefName
    val existingRemote = GithubGitHelper.getInstance().findRemote(repository, httpForkUrl, sshForkUrl)
    if (existingRemote != null) {
      val localBranch = GithubGitHelper.getInstance().findLocalBranch(repository, existingRemote, dataProvider.isFork, possibleBranchName)
      if (localBranch != null) {
        checkoutBranch(project, repository, localBranch, dataProvider)
        return
      }
    }
    checkoutNewBranch(project, repository, pullRequestNumber, dataProvider)
  }

  private fun checkoutBranch(project: Project, repository: GitRepository, localBranch: String, dataProvider: GHPRDataProvider) {
    object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.task.title"), true) {
      private val git = Git.getInstance()

      override fun run(indicator: ProgressIndicator) {
        ProgressIndicatorUtils.awaitWithCheckCanceled(dataProvider.changesData.fetchHeadBranch(), indicator)

        indicator.text = GithubBundle.message("pull.request.branch.checkout.task.indicator")
        GitBranchWorker(project, git, GitBranchUiHandlerImpl(project, git, indicator))
          .checkout(localBranch, false, listOf(repository))
      }
    }.queue()
  }

  private fun checkoutNewBranch(project: Project, repository: GitRepository, pullRequestNumber: Long, dataProvider: GHPRDataProvider) {
    val options = GitBranchUtil.getNewBranchNameFromUser(project, listOf(repository),
                                                         GithubBundle.message("pull.request.branch.checkout.create.dialog.title",
                                                                              pullRequestNumber),
                                                         generateSuggestedBranchName(repository, pullRequestNumber,
                                                                                     dataProvider), true) ?: return

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
          repository.update()
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
          repository.update()
        }
      }.queue()
    }
  }

  private fun generateSuggestedBranchName(repository: GitRepository, pullRequestNumber: Long, dataProvider: GHPRDataProvider): String =
    dataProvider.detailsData.loadedDetails.let { ghPullRequest ->
      val login = ghPullRequest?.headRepository?.owner?.login
      val headRefName = ghPullRequest?.headRefName
      when {
        headRefName == null || login == null -> "pull/${pullRequestNumber}"
        repository.branchWithTrackingExist(headRefName) -> "${login}_${headRefName}"
        else -> headRefName
      }
    }

  private fun GitRepository.branchWithTrackingExist(branchName: String) =
    branches.findLocalBranch(branchName)?.findTrackedBranch(this) != null

  private fun trySetTrackingUpstreamBranch(git: Git,
                                           repository: GitRepository,
                                           dataProvider: GHPRDataProvider,
                                           branchName: String,
                                           ghPullRequest: GHPullRequest) {
    val project = repository.project
    val vcsNotifier = project.service<VcsNotifier>()
    val pullRequestAuthor = ghPullRequest.author
    if (pullRequestAuthor == null) {
      vcsNotifier.notifyError(GithubNotificationIdsHolder.PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
                              GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"),
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
      vcsNotifier.notifyError(GithubNotificationIdsHolder.PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
                              GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"), failedMessage)
      return
    }

    val forkBranchName = "${forkRemote.name}/${ghPullRequest.headRefName}"
    val fetchResult = GitFetchSupport.fetchSupport(project).fetch(repository, forkRemote, ghPullRequest.headRefName)
    if (fetchResult.showNotificationIfFailed(GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"))) {
      val setUpstream = git.setUpstream(repository, forkBranchName, branchName)
      if (!setUpstream.success()) {
        vcsNotifier.notifyError(GithubNotificationIdsHolder.PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
                                GithubBundle.message("pull.request.branch.checkout.set.tracking.branch.failed"),
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

  private val GHPRDataProvider.isFork get() = detailsData.loadedDetails?.headRepository?.isFork ?: false
  private val GHPRDataProvider.httpForkUrl get() = detailsData.loadedDetails?.headRepository?.url
  private val GHPRDataProvider.sshForkUrl get() = detailsData.loadedDetails?.headRepository?.sshUrl
}
