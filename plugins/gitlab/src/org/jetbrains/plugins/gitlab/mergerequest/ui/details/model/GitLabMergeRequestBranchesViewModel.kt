// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.combineState
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
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

  override val sourceBranch: StateFlow<String> = with(mergeRequest) {
    combineState(cs, targetProject, sourceProject, sourceBranch) { targetProject, sourceProject, sourceBranch ->
      if (sourceProject == null) return@combineState ""
      if (targetProject == sourceProject) return@combineState sourceBranch
      val sourceProjectOwner = sourceProject.ownerPath
      return@combineState "$sourceProjectOwner:$sourceBranch"
    }
  }

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
        val sourceProject = mergeRequest.sourceProject.value ?: return
        val sourceBranch = mergeRequest.sourceBranch.value

        val headRemote = git.findOrCreateRemote(repository, sourceProject)
        if (headRemote == null) {
          notifyRemoteError(vcsNotifier, sourceProject)
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

  private fun notifyRemoteError(vcsNotifier: VcsNotifier, project: GitLabProjectDTO) {
    var failedMessage = GitLabBundle.message("merge.request.branch.checkout.resolve.remote.failed")
    val httpUrl = project.httpUrlToRepo
    val sshUrl = project.sshUrlToRepo
    if (httpUrl != null) {
      failedMessage += "\n$httpUrl"
    }
    if (sshUrl != null) {
      failedMessage += "\n$sshUrl"
    }
    vcsNotifier.notifyError(
      MERGE_REQUEST_CANNOT_SET_TRACKING_BRANCH,
      GitLabBundle.message("merge.request.branch.checkout.remote.cannot.find"),
      failedMessage
    )
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

private fun isBranchCheckedOut(repository: GitRepository, sourceBranch: String): Boolean {
  val currentBranchName = repository.currentBranchName
  return currentBranchName == sourceBranch
}

private fun Git.findOrCreateRemote(repository: GitRepository, project: GitLabProjectDTO): GitRemote? {
  val existingRemote = findRemote(repository, project)
  if (existingRemote != null) return existingRemote

  val httpUrl = project.httpUrlToRepo
  val sshUrl = project.sshUrlToRepo
  val preferHttp = shouldAddHttpRemote(repository)
  return if (preferHttp && httpUrl != null) {
    createRemote(repository, project.ownerPath, httpUrl)
  }
  else if (sshUrl != null) {
    createRemote(repository, project.ownerPath, sshUrl)
  }
  else {
    null
  }
}

private fun findRemote(repository: GitRepository, project: GitLabProjectDTO): GitRemote? =
  repository.remotes.find {
    val url = it.firstUrl
    url != null && (url.removeSuffix("/").removeSuffix(GitUtil.DOT_GIT).endsWith(project.fullPath))
  }

private fun shouldAddHttpRemote(repository: GitRepository): Boolean {
  val preferredRemoteUrl = repository.remotes.find { it.name == "origin" }?.firstUrl
                           ?: repository.remotes.firstNotNullOfOrNull { it.firstUrl }
  if (preferredRemoteUrl != null) {
    return preferredRemoteUrl.startsWith("http")
  }
  return true
}

private fun Git.createRemote(repository: GitRepository, remoteName: String, url: String): GitRemote? {
  val actualName = findNameForRemote(repository, remoteName) ?: return null
  return with(repository) {
    addRemote(this, actualName, url)
    update()
    remotes.find { it.name == actualName }
  }
}

/**
 * Returns the [preferredName] if it is not taken or adds a numerical index to it
 */
private fun findNameForRemote(repository: GitRepository, preferredName: String): String? {
  val exitingNames = repository.remotes.mapTo(mutableSetOf(), GitRemote::getName)
  if (!exitingNames.contains(preferredName)) {
    return preferredName
  }
  else {
    return sequenceOf(1..Int.MAX_VALUE).map {
      "${preferredName}_$it"
    }.find {
      exitingNames.contains(it)
    }
  }
}