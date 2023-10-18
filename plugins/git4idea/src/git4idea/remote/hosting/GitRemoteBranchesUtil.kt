// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.notification.CollaborationToolsNotificationIdsHolder
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withRawProgressReporter
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

data class HostedGitRepositoryRemote(
  val name: String,
  val serverUri: URI,
  val path: String,
  val httpUrl: String?,
  val sshUrl: String?
)

object GitRemoteBranchesUtil {

  /**
   * Checks if the current HEAD is tracking a branch on remote
   */
  fun isRemoteBranchCheckedOut(repository: GitRepository, remote: HostedGitRepositoryRemote, branchName: String): Boolean {
    val existingRemote = findRemote(repository, remote) ?: return false
    return isRemoteBranchCheckedOut(repository, GitStandardRemoteBranch(existingRemote, branchName))
  }

  fun isRemoteBranchCheckedOut(repository: GitRepository, branch: GitRemoteBranch): Boolean {
    val localBranch = findLocalBranchTrackingRemote(repository, branch) ?: return false
    return repository.currentBranchName == localBranch.name
  }

  private fun findLocalBranchTrackingRemote(repository: GitRepository, branch: GitRemoteBranch): GitLocalBranch? =
    repository.branchTrackInfos.find { it.remoteBranch == branch }?.localBranch

  suspend fun fetchAndCheckoutRemoteBranch(repository: GitRepository,
                                           remote: HostedGitRepositoryRemote,
                                           remoteBranch: String,
                                           newLocalBranchPrefix: String?) {
    val branchToCheckout = withBackgroundProgress(
      repository.project,
      CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description")
    ) {
      withRawProgressReporter {
        withContext(Dispatchers.Default) {
          findRemoteBranch(repository, remote, remoteBranch)?.takeIf {
            fetchRemoteBranch(repository, it)
          }
        }
      }
    } ?: return

    withContext(Dispatchers.Main) {
      checkoutRemoteBranch(repository, branchToCheckout, newLocalBranchPrefix)
    }
  }

  suspend fun fetchAndCheckoutRemoteBranch(repository: GitRepository, branch: GitRemoteBranch, newLocalBranchPrefix: String? = null) {
    val fetchOk = withBackgroundProgress(
      repository.project,
      CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description")
    ) {
      withRawProgressReporter {
        withContext(Dispatchers.Default) {
          fetchRemoteBranch(repository, branch)
        }
      }
    }
    if (!fetchOk) return

    withContext(Dispatchers.Main) {
      checkoutRemoteBranch(repository, branch, newLocalBranchPrefix)
    }
  }

  private suspend fun findRemoteBranch(repository: GitRepository,
                                       remote: HostedGitRepositoryRemote,
                                       remoteBranch: String): GitRemoteBranch? {
    val headRemote = coroutineToIndicator {
      Git.getInstance().findOrCreateRemote(repository, remote)
    }
    if (headRemote == null) {
      withContext(Dispatchers.Main) {
        notifyRemoteError(repository.project, remote)
      }
      return null
    }
    return GitStandardRemoteBranch(headRemote, remoteBranch)
  }

  private suspend fun fetchRemoteBranch(repository: GitRepository, branch: GitRemoteBranch): Boolean {
    val fetchResult = coroutineToIndicator {
      GitFetchSupport.fetchSupport(repository.project).fetch(repository, branch.remote, branch.nameForRemoteOperations)
    }
    return withContext(Dispatchers.Main) {
      fetchResult.showNotificationIfFailed(GitBundle.message("notification.title.fetch.failure"))
    }
  }

  fun checkoutRemoteBranch(repository: GitRepository,
                           branch: GitRemoteBranch,
                           newLocalBranchPrefix: String? = null,
                           callInAwtLater: Runnable? = null) {
    val existingLocalBranch = findLocalBranchTrackingRemote(repository, branch)
    val suggestedName = existingLocalBranch?.name
                        ?: newLocalBranchPrefix?.let { "$it/${branch.nameForRemoteOperations}" }
                        ?: branch.nameForRemoteOperations

    GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction
      .checkoutRemoteBranch(repository.project, listOf(repository), branch.name, suggestedName, callInAwtLater)
  }

  private fun notifyRemoteError(project: Project, remote: HostedGitRepositoryRemote) {
    var failedMessage = CollaborationToolsBundle.message("review.details.action.branch.checkout.failed.remote")
    val httpUrl = remote.httpUrl //NON-NLS
    val sshUrl = remote.sshUrl //NON-NLS
    if (httpUrl != null) {
      failedMessage += "\n$httpUrl"
    }
    if (sshUrl != null) {
      failedMessage += "\n$sshUrl"
    }
    VcsNotifier.getInstance(project).notifyError(
      CollaborationToolsNotificationIdsHolder.REVIEW_BRANCH_CHECKOUT_FAILED,
      CollaborationToolsBundle.message("review.details.action.branch.checkout.failed"),
      failedMessage
    )
  }

  private fun Git.findOrCreateRemote(repository: GitRepository, remote: HostedGitRepositoryRemote): GitRemote? {
    val existingRemote = findRemote(repository, remote)
    if (existingRemote != null) return existingRemote

    val httpUrl = remote.httpUrl
    val sshUrl = remote.sshUrl
    val preferHttp = shouldAddHttpRemote(repository)
    return if (preferHttp && httpUrl != null) {
      createRemote(repository, remote.name, httpUrl)
    }
    else if (sshUrl != null) {
      createRemote(repository, remote.name, sshUrl)
    }
    else {
      null
    }
  }

  fun findRemote(repository: GitRepository, remote: HostedGitRepositoryRemote): GitRemote? =
    repository.remotes.find {
      val url = it.firstUrl
      url != null &&
      GitHostingUrlUtil.match(remote.serverUri, url) &&
      (url.removeSuffix("/").removeSuffix(GitUtil.DOT_GIT).endsWith(remote.path))
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
}