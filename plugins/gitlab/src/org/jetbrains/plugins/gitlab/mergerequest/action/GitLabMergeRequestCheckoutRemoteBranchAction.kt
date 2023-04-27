// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabMergeRequestCheckoutRemoteBranchAction : DumbAwareAction(
  CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action"),
  CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description"),
  null
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project: Project? = e.getData(CommonDataKeys.PROJECT)
    val repository: GitRepository? = e.getData(GitLabMergeRequestsActionKeys.GIT_REPOSITORY)
    val mergeRequest: GitLabMergeRequest? = e.getData(GitLabMergeRequestsActionKeys.MERGE_REQUEST)

    e.presentation.text = CollaborationToolsBundle.message("review.details.branch.checkout.remote", mergeRequest?.sourceBranch?.value)
    e.presentation.isEnabled = project != null && !project.isDefault && repository != null && mergeRequest != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project: Project = e.getRequiredData(CommonDataKeys.PROJECT)
    val repository: GitRepository = e.getRequiredData(GitLabMergeRequestsActionKeys.GIT_REPOSITORY)
    val mergeRequest: GitLabMergeRequest = e.getRequiredData(GitLabMergeRequestsActionKeys.MERGE_REQUEST)

    val vcsNotifier = project.service<VcsNotifier>()
    fetchAndCheckoutRemoteBranch(project, repository, mergeRequest, vcsNotifier)
  }

  private fun fetchAndCheckoutRemoteBranch(
    project: Project,
    repository: GitRepository,
    mergeRequest: GitLabMergeRequest,
    vcsNotifier: VcsNotifier
  ) {
    object : Task.Backgroundable(project, "", true) {
      private val git = Git.getInstance()

      override fun run(indicator: ProgressIndicator) {
        val sourceBranch = mergeRequest.sourceBranch.value
        val httpForkUrl = mergeRequest.sourceProject.value.httpUrlToRepo
        val pullRequestAuthor = mergeRequest.author

        val headRemote = git.findOrCreateRemote(repository, pullRequestAuthor.username, httpForkUrl)
        if (headRemote == null) {
          notifyRemoteError(vcsNotifier, httpForkUrl)
          return
        }

        val fetchResult = GitFetchSupport.fetchSupport(project).fetch(repository, headRemote, sourceBranch)
        if (fetchResult.showNotificationIfFailed(GitBundle.message("branches.update.failed"))) {
          val branch = "${headRemote.name}/${sourceBranch}"
          invokeLater {
            CheckoutRemoteBranchAction.checkoutRemoteBranch(project, listOf(repository), branch)
          }
        }
      }

      private fun notifyRemoteError(vcsNotifier: VcsNotifier, httpForkUrl: @NlsSafe String?) {
        var failedMessage = GitLabBundle.message("merge.request.branch.checkout.resolve.remote.failed")
        if (httpForkUrl != null) {
          failedMessage += "\n$httpForkUrl"
        }
        vcsNotifier.notifyError(
          MERGE_REQUEST_CANNOT_SET_TRACKING_BRANCH,
          GitLabBundle.message("merge.request.branch.checkout.remote.cannot.find"),
          failedMessage
        )
      }
    }.queue()
  }

  // TODO: implement logic use sshUrlToRepo
  private fun Git.findOrCreateRemote(repository: GitRepository, remoteName: String, httpUrl: String?): GitRemote? {
    val existingRemote = findRemote(repository, httpUrl)
    if (existingRemote != null) return existingRemote

    if (httpUrl != null && repository.remotes.any { it.name == remoteName }) {
      return createRemote(repository, "pull_$remoteName", httpUrl)
    }

    return when {
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

  private fun findRemote(repository: GitRepository, httpUrl: String?): GitRemote? =
    repository.remotes.find {
      it.firstUrl != null && (it.firstUrl == httpUrl ||
                              it.firstUrl == httpUrl + GitUtil.DOT_GIT)
    }

  companion object {
    private const val MERGE_REQUEST_CANNOT_SET_TRACKING_BRANCH = "gitlab.merge.request.cannot.set.tracking.branch"
  }
}