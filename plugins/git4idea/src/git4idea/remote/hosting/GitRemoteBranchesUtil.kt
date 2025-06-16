// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.notification.CollaborationToolsNotificationIdsHolder
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.*
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchDialog
import git4idea.branch.GitNewBranchOptions
import git4idea.commands.Git
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.push.GitSpecialRefRemoteBranch
import git4idea.repo.GitRemote
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchCheckoutOperation
import git4idea.ui.branch.hasTrackingConflicts
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

data class HostedGitRepositoryRemoteBranch(
  val remote: HostedGitRepositoryRemote,
  val branchName: String
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
    return when (branch) {
      is GitSpecialRefRemoteBranch -> {
        val hash = Git.getInstance().resolveReference(repository, branch.nameForLocalOperations)?.asString()
        repository.currentRevision == hash
      }
      else -> {
        val localBranch = findLocalBranchTrackingRemote(repository, branch) ?: return false
        repository.currentBranchName == localBranch.name
      }
    }
  }

  private fun findLocalBranchTrackingRemote(repository: GitRepository, branch: GitRemoteBranch): GitLocalBranch? =
    repository.branchTrackInfos.find { it.remoteBranch == branch }?.localBranch

  fun findCurrentRemoteBranch(gitRepo: GitRepository, remote: GitRemote): String? {
    val currentBranch = gitRepo.currentBranch ?: return null
    return gitRepo.branchTrackInfos.find { it.localBranch == currentBranch && it.remote == remote }
      ?.remoteBranch?.nameForRemoteOperations
  }

  suspend fun fetchAndCheckoutRemoteBranch(repository: GitRepository,
                                           remote: HostedGitRepositoryRemote,
                                           remoteBranch: String,
                                           newLocalBranchPrefix: String?) {
    withBackgroundProgress(repository.project,
                           CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description")) {
      val branch = findOrCreateRemoteBranch(repository, remote, remoteBranch) ?: return@withBackgroundProgress

      val fetchOk = fetchBranch(repository, branch)
      if (!fetchOk) return@withBackgroundProgress

      withContext(Dispatchers.Main) {
        checkoutRemoteBranch(repository, branch, newLocalBranchPrefix)
      }
    }
  }

  suspend fun fetchAndCheckoutRemoteBranch(repository: GitRepository, branch: GitRemoteBranch, newLocalBranchPrefix: String? = null) {
    withBackgroundProgress(repository.project,
                           CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description")) {
      val fetchOk = fetchBranch(repository, branch)
      if (!fetchOk) return@withBackgroundProgress

      withContext(Dispatchers.Main) {
        checkoutRemoteBranch(repository, branch, newLocalBranchPrefix)
      }
    }
  }

  suspend fun fetchAndShowRemoteBranchInLog(repository: GitRepository,
                                            remote: HostedGitRepositoryRemote,
                                            remoteBranch: String,
                                            targetBranch: String?) {
    withBackgroundProgress(repository.project,
                           CollaborationToolsBundle.message("review.details.action.branch.show.remote.branch.in.log.action.description")) {
      val branchRef = findOrCreateRemoteBranch(repository, remote, remoteBranch) ?: return@withBackgroundProgress
      val targetBranchRef = targetBranch?.let { findOrCreateRemoteBranch(repository, remote, targetBranch) }

      val fetchOk = fetchBranch(repository, branchRef)
      if (!fetchOk) return@withBackgroundProgress

      showRemoteBranchInLog(repository, branchRef, targetBranchRef)
    }
  }

  suspend fun fetchAndShowRemoteBranchInLog(repository: GitRepository, branch: GitRemoteBranch, targetBranch: GitRemoteBranch?) {
    withBackgroundProgress(repository.project,
                           CollaborationToolsBundle.message("review.details.action.branch.show.remote.branch.in.log.action.description")) {
      val fetchOk = fetchBranch(repository, branch)
      if (!fetchOk) return@withBackgroundProgress

      showRemoteBranchInLog(repository, branch, targetBranch)
    }
  }

  private suspend fun fetchBranch(repository: GitRepository, branch: GitRemoteBranch): Boolean {
    val fetchResult = indeterminateStep {
      withRawProgressReporter {
        withContext(Dispatchers.Default) {
          coroutineToIndicator {
            val refspec = when (branch) {
              is GitSpecialRefRemoteBranch -> "${branch.nameForRemoteOperations}:${branch.nameForLocalOperations}"
              else -> branch.nameForRemoteOperations
            }
            GitFetchSupport.fetchSupport(repository.project).fetch(repository, branch.remote, refspec)
          }
        }
      }
    }
    return withContext(Dispatchers.Main) {
      fetchResult.showNotificationIfFailed(GitBundle.message("notification.title.fetch.failure"))
    }
  }

  fun findRemoteBranch(
    repositoryInfo: GitRepoInfo,
    branch: HostedGitRepositoryRemoteBranch,
  ): GitRemoteBranch? {
    val headRemote = findRemote(repositoryInfo, branch.remote) ?: return null
    return GitStandardRemoteBranch(headRemote, branch.branchName)
  }

  fun findRemoteBranch(
    repositoryInfo: GitRepoInfo,
    remote: HostedGitRepositoryRemote,
    remoteBranch: String,
  ): GitRemoteBranch? {
    val headRemote = findRemote(repositoryInfo, remote) ?: return null
    return GitStandardRemoteBranch(headRemote, remoteBranch)
  }

  suspend fun findOrCreateRemoteBranch(
    repository: GitRepository,
    remote: HostedGitRepositoryRemote,
    remoteBranch: String,
  ): GitRemoteBranch? {
    val headRemote = findOrCreateRemote(repository, remote)
    if (headRemote == null) {
      notifyRemoteError(repository.project, remote)
      return null
    }
    return GitStandardRemoteBranch(headRemote, remoteBranch)
  }

  @RequiresEdt
  fun checkoutRemoteBranch(repository: GitRepository,
                           branch: GitRemoteBranch,
                           newLocalBranchPrefix: String? = null,
                           callInAwtLater: Runnable? = null) {
    when (branch) {
      // For special refs, there's no backing remote branch.
      // We check out in detached HEAD to avoid confusion from pull/push actions.
      is GitSpecialRefRemoteBranch -> {
        GitBrancher.getInstance(repository.project)
          .checkout(branch.name, true, listOf(repository), callInAwtLater)
      }
      else -> {
        val existingLocalBranch = findLocalBranchTrackingRemote(repository, branch)
        val suggestedName = existingLocalBranch?.name
                            ?: newLocalBranchPrefix?.let { "$it/${branch.nameForRemoteOperations}" }
                            ?: branch.nameForRemoteOperations

        checkoutRemoteBranch(repository.project, listOf(repository), branch.name, suggestedName, callInAwtLater)
      }
    }
  }

  @RequiresEdt
  @JvmStatic
  fun checkoutRemoteBranch(project: Project, repositories: List<GitRepository>, remoteBranchName: String) {
    val suggestedLocalName = repositories.firstNotNullOf { it.branches.findRemoteBranch(remoteBranchName)?.nameForRemoteOperations }
    checkoutRemoteBranch(project, repositories, remoteBranchName, suggestedLocalName, null)
  }

  @RequiresEdt
  private fun checkoutRemoteBranch(project: Project, repositories: List<GitRepository>, remoteBranchName: String, suggestedLocalName: String, callInAwtLater: Runnable?) {
    // can have remote conflict if git-svn is used - suggested local name will be equal to selected remote
    if (GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, suggestedLocalName)) {
      askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName, callInAwtLater)
      return
    }
    val conflictingLocalBranches = repositories.mapNotNull { repo ->
      repo.branches.findLocalBranch(suggestedLocalName)?.let { repo to it }
    }.toMap()
    if (hasTrackingConflicts(conflictingLocalBranches, remoteBranchName)) {
      askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName, callInAwtLater)
    } else {
      GitBranchCheckoutOperation(project, repositories)
        .perform(remoteBranchName, GitNewBranchOptions(suggestedLocalName, true, true, false, repositories), callInAwtLater)
    }
  }

  @RequiresEdt
  private fun askNewBranchNameAndCheckout(
    project: Project, repositories: List<GitRepository>, remoteBranchName: String, suggestedLocalName: String, callInAwtLater: Runnable?,
  ) {
    // Do not allow name conflicts
    val options = GitNewBranchDialog(
      project,
      repositories,
      GitBundle.message("branches.checkout.s", remoteBranchName),
      suggestedLocalName,
      false,
      true
    ).showAndGetOptions() ?: return

    GitBrancher.getInstance(project).checkoutNewBranchStartingFrom(options.name,
                                                                   remoteBranchName,
                                                                   options.reset,
                                                                   options.repositories.toList(),
                                                                   callInAwtLater)
  }

  private suspend fun showRemoteBranchInLog(repository: GitRepository,
                                            branch: GitRemoteBranch,
                                            targetBranch: GitRemoteBranch?) {
    withContext(Dispatchers.Main) {
      val branchFilter = if (targetBranch != null) {
        VcsLogFilterObject.fromRange(targetBranch.nameForLocalOperations, branch.nameForLocalOperations)
      }
      else {
        VcsLogFilterObject.fromBranch(branch.nameForLocalOperations)
      }
      val repoFilter = VcsLogFilterObject.fromRoots(listOf(repository.root))
      val filters = VcsLogFilterObject.collection(branchFilter, repoFilter)
      VcsProjectLog.getInstance(repository.project).openLogTab(filters)
    }
  }

  private suspend fun notifyRemoteError(project: Project, remote: HostedGitRepositoryRemote) {
    withContext(Dispatchers.Main) {
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
  }

  suspend fun findOrCreateRemote(repository: GitRepository, remote: HostedGitRepositoryRemote): GitRemote? {
    return indeterminateStep {
      withRawProgressReporter {
        withContext(Dispatchers.Default) {
          coroutineToIndicator {
            Git.getInstance().findOrCreateRemote(repository, remote)
          }
        }
      }
    }
  }

  @RequiresBackgroundThread
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
    findRemote(repository.info, remote)

  fun findRemote(repositoryInfo: GitRepoInfo, remote: HostedGitRepositoryRemote): GitRemote? =
    repositoryInfo.remotes.find {
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
    val exitingNames = repository.remotes.mapTo(mutableSetOf(), GitRemote::name)
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
