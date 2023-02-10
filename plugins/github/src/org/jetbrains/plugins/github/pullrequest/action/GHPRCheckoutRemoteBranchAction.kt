// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubNotificationIdsHolder
import org.jetbrains.plugins.github.util.GithubSettings

class GHPRCheckoutRemoteBranchAction : DumbAwareAction(
  CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action"),
  CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description"),
  null
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project: Project? = e.getData(CommonDataKeys.PROJECT)
    val repository: GitRepository? = e.getData(GHPRActionKeys.GIT_REPOSITORY)
    val dataProvider: GHPRDataProvider? = e.getData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)

    e.presentation.text = CollaborationToolsBundle.message("review.details.branch.checkout.remote",
                                                           dataProvider?.detailsData?.loadedDetails?.headRefName)
    e.presentation.isEnabled = project != null && !project.isDefault && repository != null && dataProvider != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project: Project = e.getRequiredData(CommonDataKeys.PROJECT)
    val repository: GitRepository = e.getRequiredData(GHPRActionKeys.GIT_REPOSITORY)
    val dataProvider: GHPRDataProvider = e.getRequiredData(GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER)

    val vcsNotifier = project.service<VcsNotifier>()
    val pullRequest = ProgressIndicatorUtils.awaitWithCheckCanceled(dataProvider.detailsData.loadDetails(), EmptyProgressIndicator())
    if (pullRequest.author == null) {
      vcsNotifier.notifyError(
        GithubNotificationIdsHolder.PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
        GithubBundle.message("pull.request.branch.checkout.remote.cannot.find"),
        GithubBundle.message("pull.request.branch.checkout.resolve.author.failed")
      )
      return
    }

    fetchAndCheckoutRemoteBranch(project, repository, pullRequest, vcsNotifier)
  }

  private fun fetchAndCheckoutRemoteBranch(
    project: Project,
    repository: GitRepository,
    pullRequest: GHPullRequest,
    vcsNotifier: VcsNotifier
  ) {
    object : Task.Backgroundable(project, GithubBundle.message("pull.request.branch.checkout.task.indicator"), true) {
      private val git = Git.getInstance()

      override fun run(indicator: ProgressIndicator) {
        val headBranch = pullRequest.headRefName
        val httpForkUrl = pullRequest.headRepository?.url
        val sshForkUrl = pullRequest.headRepository?.sshUrl
        val pullRequestAuthor = pullRequest.author!!

        val headRemote = git.findOrCreateRemote(repository, pullRequestAuthor.login, httpForkUrl, sshForkUrl)
        if (headRemote == null) {
          notifyRemoteError(vcsNotifier, httpForkUrl, sshForkUrl)
          return
        }

        val fetchResult = GitFetchSupport.fetchSupport(project).fetch(repository, headRemote, headBranch)
        if (fetchResult.showNotificationIfFailed(GitBundle.message("branches.update.failed"))) {
          val branch = "${headRemote.name}/${headBranch}"
          invokeLater {
            CheckoutRemoteBranchAction.checkoutRemoteBranch(project, listOf(repository), branch)
          }
        }
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
    }.queue()
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
}