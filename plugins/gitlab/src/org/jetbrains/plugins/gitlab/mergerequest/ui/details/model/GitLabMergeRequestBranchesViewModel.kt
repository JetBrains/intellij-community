// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.messages.CollaborationToolsBundle
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
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.ui.branch.GitBranchPopupActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabMergeRequestBranchesViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  private val repository: GitRepository
) : CodeReviewBranchesViewModel {
  private val git: Git = Git.getInstance()
  private val vcsNotifier: VcsNotifier = project.service<VcsNotifier>()

  private val cs: CoroutineScope = parentCs.childScope()

  private val targetProject: StateFlow<GitLabProjectDTO> = mergeRequest.targetProject
  private val sourceProject: StateFlow<GitLabProjectDTO?> = mergeRequest.sourceProject

  override val sourceBranch: StateFlow<String> =
    combine(targetProject, sourceProject, mergeRequest.sourceBranch) { targetProject, sourceProject, sourceBranch ->
      if (sourceProject == null) return@combine ""
      if (targetProject == sourceProject) return@combine sourceBranch
      val sourceProjectOwner = sourceProject.fullPath.split("/").dropLast(1).joinToString("/")
      return@combine "$sourceProjectOwner:$sourceBranch"
    }.stateIn(cs, SharingStarted.Lazily, mergeRequest.sourceBranch.value)

  override val isCheckedOut: SharedFlow<Boolean> = callbackFlow {
    val cs = this
    send(isBranchCheckedOut(repository, sourceBranch.value))

    project.messageBus
      .connect(cs)
      .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        trySend(isBranchCheckedOut(it, sourceBranch.value))
      })

    sourceBranch.collect { sourceBranch ->
      send(isBranchCheckedOut(repository, sourceBranch))
    }
  }.modelFlow(cs, thisLogger())

  private val _showBranchesRequests = MutableSharedFlow<CodeReviewBranches>()
  override val showBranchesRequests: SharedFlow<CodeReviewBranches> = _showBranchesRequests

  override fun fetchAndCheckoutRemoteBranch() {
    object : Task.Backgroundable(
      project,
      CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description"),
      true
    ) {
      override fun run(indicator: ProgressIndicator) {
        val sourceBranch = sourceBranch.value
        val sourceProject = sourceProject.value ?: return
        val httpForkUrl = sourceProject.httpUrlToRepo ?: return
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
            GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction.checkoutRemoteBranch(project, listOf(repository), branch)
          }
        }
      }
    }.queue()
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

  private fun isBranchCheckedOut(repository: GitRepository, sourceBranch: String): Boolean {
    val currentBranchName = repository.currentBranchName
    return currentBranchName == sourceBranch
  }

  private fun findRemote(repository: GitRepository, httpUrl: String?): GitRemote? =
    repository.remotes.find {
      it.firstUrl != null && (it.firstUrl == httpUrl ||
                              it.firstUrl == httpUrl + GitUtil.DOT_GIT)
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

  override fun showBranches() {
    cs.launchNow {
      val source = sourceBranch.value
      val target = mergeRequest.targetBranch.value
      _showBranchesRequests.emit(CodeReviewBranches(source, target))
    }
  }

  companion object {
    private const val MERGE_REQUEST_CANNOT_SET_TRACKING_BRANCH = "gitlab.merge.request.cannot.set.tracking.branch"
  }
}