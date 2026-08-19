// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.notification.NotificationAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.mapConcurrent
import com.intellij.platform.util.progress.reportProgressScope
import git4idea.GitDisposable
import git4idea.GitLocalBranch
import git4idea.GitNotificationIdsHolder.Companion.BRANCHES_UPDATE_SUCCESSFUL
import git4idea.GitNotificationIdsHolder.Companion.WORKTREE_BRANCH_UPDATE_FAILED
import git4idea.GitReference
import git4idea.GitUtil
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction.Companion.findCheckedOutWorkingTrees
import git4idea.branch.GitBranchPair
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitNewBranchDialog
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchSpec
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitBranchTrackInfo
import git4idea.repo.GitRepository
import git4idea.update.GitUpdateExecutionProcess
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path

private val LOG: Logger = fileLogger()

private const val MAX_CONCURRENT_OTHER_WORKTREE_UPDATES_REGISTRY_KEY = "git.branches.update.other.worktree.max.concurrent"
private const val DEFAULT_MAX_CONCURRENT_OTHER_WORKTREE_UPDATES = 10

@JvmOverloads
internal fun createOrCheckoutNewBranch(project: Project,
                                       repositories: Collection<GitRepository>,
                                       startPoint: String,
                                       @Nls(capitalization = Nls.Capitalization.Title)
                                       title: String = GitBundle.message("branches.create.new.branch.dialog.title"),
                                       initialName: String? = null) {
  val options = GitNewBranchDialog(project, repositories, title, initialName, showResetOption = true, localConflictsAllowed = true).showAndGetOptions() ?: return
  GitBranchCheckoutOperation(project, options.repositories).perform(startPoint, options)
}

internal fun updateBranches(project: Project, repositories: Collection<GitRepository>, localBranchNames: List<String>): Job {
  val repoToTrackingInfos =
    repositories.associateWith { it.branchTrackInfos.filter { info -> localBranchNames.contains(info.localBranch.name) } }
  if (repoToTrackingInfos.isEmpty()) return CompletableDeferred(Unit)

  return GitDisposable.getInstance(project).coroutineScope.launch {
    withBackgroundProgress(project, GitBundle.message("branches.updating.process")) {
      // If a branch is checked out, do update via GitUpdateExecutionProcess
      val updateProcessTargets = HashMap<GitRepository, GitBranchPair>()
      // Otherwise, perform fetch using remote:local refspec
      val fetchTargets = mutableListOf<GitFetchSpec>()
      // If the branch is checked out in another worktree, that ref can't be moved from here: fast-forward it there instead
      val otherWorktreeTargets = mutableListOf<Pair<GitWorkingTree, GitBranchTrackInfo>>()

      for ((repo, trackingInfos) in repoToTrackingInfos) {
        val currentBranch = repo.currentBranch
        for (trackingInfo in trackingInfos) {
          val localBranch = trackingInfo.localBranch
          val remoteBranch = trackingInfo.remoteBranch
          val workingTreesWithBranch = findCheckedOutWorkingTrees(localBranch, listOf(repo), skipCurrentWorkingTree = true)
          when {
            localBranch == currentBranch -> {
              updateProcessTargets[repo] = GitBranchPair(currentBranch, remoteBranch)
            }
            workingTreesWithBranch.isNotEmpty() -> {
              workingTreesWithBranch.forEach { workingTree -> otherWorktreeTargets.add(workingTree to trackingInfo) }
            }
            else -> {
              // Fast-forward all non-current branches in the selection
              val localBranchName = localBranch.name
              val remoteBranchName = remoteBranch.nameForRemoteOperations
              fetchTargets.add(GitFetchSpec(repo, trackingInfo.remote, "$remoteBranchName:$localBranchName"))
            }
          }
        }
      }

      if (fetchTargets.isNotEmpty()) {
        val fetchSuccessful = coroutineToIndicator {
          GitFetchSupport.fetchSupport(project).fetch(fetchTargets).showNotificationIfFailed(GitBundle.message("branches.update.failed"))
        }
        if (fetchSuccessful) {
          VcsNotifier.getInstance(project).notifySuccess(BRANCHES_UPDATE_SUCCESSFUL, "", GitBundle.message("branches.fetch.finished", fetchTargets.size))
        }
      }

      // Each worktree lives in its own directory, so these fetch+merge calls are independent and safe to run concurrently.
      // A per-task catch keeps one worktree's unexpected failure from cancelling its siblings or this whole update.
      val maxConcurrentUpdates = Registry.intValue(MAX_CONCURRENT_OTHER_WORKTREE_UPDATES_REGISTRY_KEY, DEFAULT_MAX_CONCURRENT_OTHER_WORKTREE_UPDATES)
      val updatedCount = reportProgressScope(otherWorktreeTargets.size) { reporter ->
        otherWorktreeTargets.mapConcurrent(concurrency = maxConcurrentUpdates) { (workingTree, trackingInfo) ->
          reporter.itemStep {
            withContext(Dispatchers.IO) {
              try {
                updateBranchInOtherWorktree(project, workingTree, trackingInfo)
              }
              catch (e: Exception) {
                rethrowControlFlowException(e)
                LOG.warn("Failed to update branch ${trackingInfo.localBranch.name} in worktree ${workingTree.path.path}", e)
                false
              }
            }
          }
        }
      }.count { it }
      if (updatedCount > 0) {
        VcsNotifier.getInstance(project)
          .notifySuccess(BRANCHES_UPDATE_SUCCESSFUL, "", GitBundle.message("branches.update.other.worktree.finished", updatedCount))
      }

      if (updateProcessTargets.isNotEmpty()) {
        GitUpdateExecutionProcess.update(project,
                                         repositories,
                                         updateProcessTargets,
                                         GitVcsSettings.getInstance(project).updateMethod,
                                         false)
      }
    }
  }
}

/**
 * Fast-forwards [trackingInfo.localBranch][GitBranchTrackInfo.getLocalBranch] to its tracked remote branch, rooted at [workingTree]'s own
 * path rather than the current repository's root. This is the only way to update a branch checked out in another worktree: git refuses
 * to move a ref checked out elsewhere, but an ordinary fetch+merge rooted at that worktree's own directory is just a local update there.
 *
 * Uses raw [GitLineHandler] fetch+merge commands rather than [Git.fetch]/[Git.merge]: those always run at
 * `repository.root`, with no way to target a different path, so they can't be pointed at [workingTree]'s own directory.
 */
private suspend fun updateBranchInOtherWorktree(
  project: Project,
  workingTree: GitWorkingTree,
  trackingInfo: GitBranchTrackInfo,
): Boolean = coroutineToIndicator {
  val root = Path.of(workingTree.path.path)
  val remote = trackingInfo.remote
  val branchName = trackingInfo.localBranch.name

  val fetchHandler = GitLineHandler(project, root, GitCommand.FETCH)
  fetchHandler.setUrls(remote.urls)
  fetchHandler.addParameters(remote.name, trackingInfo.remoteBranch.nameForRemoteOperations)
  val fetchResult = Git.getInstance().runCommand(fetchHandler)
  if (!fetchResult.success()) {
    notifyOtherWorktreeUpdateFailed(project, workingTree, branchName)
    return@coroutineToIndicator false
  }

  val mergeHandler = GitLineHandler(project, root, GitCommand.MERGE)
  mergeHandler.addParameters("FETCH_HEAD", "--ff-only")
  val mergeResult = Git.getInstance().runCommand(mergeHandler)
  if (!mergeResult.success()) {
    notifyOtherWorktreeUpdateFailed(project, workingTree, branchName)
    return@coroutineToIndicator false
  }
  true
}

private fun notifyOtherWorktreeUpdateFailed(project: Project, workingTree: GitWorkingTree, branchName: String) {
  val message = GitBundle.message("branches.update.other.worktree.failed", branchName, getPresentablePath(workingTree.path.path))
  VcsNotifier.getInstance(project).notifyError(
    WORKTREE_BRANCH_UPDATE_FAILED,
    GitBundle.message("branches.update.failed"),
    message,
    NotificationAction.createSimple(GitBundle.messagePointer("action.open.worktree.for.a.branch.text")) {
      GitWorkingTreesService.getInstance(project).openWorkingTreeProject(workingTree)
    }
  )
}

internal fun isTrackingInfosExist(branchNames: List<String>, repositories: Collection<GitRepository>) =
  repositories
    .flatMap(GitRepository::getBranchTrackInfos)
    .any { trackingBranchInfo -> branchNames.any { branchName -> branchName == trackingBranchInfo.localBranch.name } }

internal fun hasRemotes(project: Project): Boolean {
  return hasAnyRemotes(GitUtil.getRepositories(project))
}

internal fun hasAnyRemotes(repositories: Collection<GitRepository>): Boolean = repositories.any { it.remotes.isNotEmpty() }

internal fun hasTrackingConflicts(conflictingLocalBranches: Map<GitRepository, GitLocalBranch>,
                                  remoteBranchName: String): Boolean =
  conflictingLocalBranches.any { (repo, branch) ->
    val trackInfo = GitBranchUtil.getTrackInfoForBranch(repo, branch)
    trackInfo != null && !GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, trackInfo.remoteBranch.name)
  }
