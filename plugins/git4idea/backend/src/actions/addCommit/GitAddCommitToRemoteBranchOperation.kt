// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.addCommit

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.vcs.console.VcsConsoleTabService
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.impl.HashImpl
import git4idea.DialogManager
import git4idea.GitNotificationIdsHolder
import git4idea.GitRemoteBranch
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.fetch.GitFetchSpec
import git4idea.fetch.GitFetchSupport
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.MergeConflictException
import git4idea.inMemory.objects.Oid
import git4idea.inMemory.rebase.checkInMemoryRebaseSupport
import git4idea.inMemory.rebaseCommit
import git4idea.push.GitPushSource
import git4idea.push.GitPushTarget
import git4idea.push.GitPushTargetType
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

private val LOG = logger<GitAddCommitToRemoteBranchOperation>()

/**
 * Cherry-picks [commits] onto a base commit in memory and opens the push dialog on the result.
 * The commits can come in any order. The operation applies them parents-first.
 * A commit that already sits on the current base is reused as is, so its hash does not change.
 *
 * [baseMode] selects the base commit:
 * - [BaseMode.FETCHED_REMOTE_TIP] fetches [remoteBranch] first and uses its tip.
 * - [BaseMode.MERGE_BASE_WITH_HEAD] does not fetch. The base is the merge base of `HEAD`
 *   and the local remote-tracking ref of [remoteBranch]. The result is the current `HEAD`
 *   minus the commits that are not selected. When the branches have diverged, the push needs force.
 */
@ApiStatus.Internal
class GitAddCommitToRemoteBranchOperation(
  private val project: Project,
  private val repository: GitRepository,
  private val commits: List<VcsFullCommitDetails>,
  private val remoteBranch: GitRemoteBranch,
  private val cs: CoroutineScope,
  private val baseMode: BaseMode = BaseMode.FETCHED_REMOTE_TIP,
) {
  enum class BaseMode {
    FETCHED_REMOTE_TIP,
    MERGE_BASE_WITH_HEAD,
  }

  suspend fun execute() {
    withBackgroundProgress(project, title = GitBundle.message("progress.title.adding.commits.to.remote.branch", commits.size, remoteBranch.nameForRemoteOperations), cancellable = true) {
      try {
        assertMergeTreeSupported()

        if (baseMode == BaseMode.FETCHED_REMOTE_TIP) {
          withProgressText(GitBundle.message("progress.text.fetching.remote.branch")) {
            fetchRemoteBranch()
          }
        }

        val newRemoteBranchOid = withProgressText(GitBundle.message("progress.text.cherry.picking.commits")) {
          cherryPickCommitsInMemory()
        }

        if (newRemoteBranchOid == null) {
          notifyNothingToAdd()
        }
        else {
          cs.launch(Dispatchers.EDT) {
            showPushDialog(newRemoteBranchOid)
          }
        }
      }
      catch (e: MergeConflictException) {
        LOG.info("Merge conflict while adding commits to remote branch", e)
        handleMergeConflict(e)
      }
      catch (e: VcsException) {
        LOG.warn("Failed to add commits to remote branch", e)
        handleError(e)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)

        LOG.warn("Unexpected error while adding commits to remote branch", e)
        handleError(VcsException(e))
      }
    }
  }

  private fun assertMergeTreeSupported() {
    val unsupported = checkInMemoryRebaseSupport(repository) ?: return
    throw VcsException(GitBundle.message("notification.content.add.to.remote.branch.unsupported.git.version",
                                         unsupported.requiredVersion.presentation))
  }

  private fun fetchRemoteBranch() {
    val remote = remoteBranch.remote
    val branchName = remoteBranch.nameForRemoteOperations

    LOG.info("Fetching remote branch $remote/$branchName")

    val fetchSpec = GitFetchSpec(
      repository = repository,
      remote = remote,
      refspec = "refs/heads/$branchName:refs/remotes/${remote.name}/$branchName",
      unshallow = false,
    )

    val result = GitFetchSupport.fetchSupport(project).fetch(listOf(fetchSpec))
    result.throwExceptionIfFailed()

    repository.update()
    LOG.info("Successfully fetched remote branch $remote/$branchName")
  }

  private fun cherryPickCommitsInMemory(): Oid? {
    val objectRepo = GitObjectRepository(repository)

    val remote = remoteBranch.remote
    val branchName = remoteBranch.nameForRemoteOperations
    val remoteBranchRef = "refs/remotes/${remote.name}/$branchName"

    val baseHash = resolveBaseHash(remoteBranchRef)

    LOG.info("Cherry-pick base: $baseHash")

    var currentBase = objectRepo.findCommit(Oid.fromHash(baseHash))

    // Cherry-pick each commit sequentially
    for (commitDetails in sortParentsFirst(commits, baseHash)) {
      val commitOid = Oid.fromHash(commitDetails.id)
      val commit = objectRepo.findCommit(commitOid)

      if (commit.parentsOids.singleOrNull() == currentBase.oid) {
        LOG.info("Fast-forwarding to commit ${commit.oid}")
        currentBase = commit
        continue
      }

      LOG.info("Cherry-picking commit ${commit.oid} onto ${currentBase.oid}")

      val newCommitOid = objectRepo.rebaseCommit(commit, currentBase)
      if (newCommitOid == null) {
        LOG.info("Skipping commit ${commit.oid}: changes already present on ${currentBase.oid}")
        continue
      }

      currentBase = objectRepo.findCommit(newCommitOid)

      LOG.info("Created new commit: ${currentBase.oid}")
    }

    if (currentBase.oid == Oid.fromHash(baseHash)) {
      LOG.info("All commits already present on ${remoteBranch.nameForLocalOperations}; nothing to push")
      return null
    }

    return currentBase.oid
  }

  private fun resolveBaseHash(remoteBranchRef: String): Hash = when (baseMode) {
    BaseMode.FETCHED_REMOTE_TIP -> {
      Git.getInstance().resolveReference(repository, remoteBranchRef)
      ?: throw VcsException(GitBundle.message("error.cannot.resolve.reference", remoteBranchRef))
    }
    BaseMode.MERGE_BASE_WITH_HEAD -> {
      val mergeBase = GitHistoryUtils.getMergeBase(project, repository.root, GitUtil.HEAD, remoteBranchRef)
                      ?: throw VcsException(GitBundle.message("error.cannot.find.merge.base", remoteBranchRef))
      HashImpl.build(mergeBase.rev)
    }
  }

  /**
   * Sorts the selected commits in the parents-first order with `git rev-list --topo-order`.
   * The ancestry between two selected commits can pass through unselected commits.
   * Commits that are already reachable from [baseHash] go first, also parents-first.
   * The cherry-pick skips them when the base still has their changes.
   * It applies them again, in the ancestry order, when a later commit reverted them.
   */
  private fun sortParentsFirst(commits: List<VcsFullCommitDetails>, baseHash: Hash): List<VcsFullCommitDetails> {
    if (commits.size < 2) return commits

    val byHash = commits.associateBy { it.id.asString() }
    val childrenFirst = revListChildrenFirst(byHash.keys, boundary = baseHash.asString()).mapNotNull { byHash[it] }
    val reachableFromBase = commits - childrenFirst.toSet()
    return sortReachableParentsFirst(reachableFromBase) + childrenFirst.asReversed()
  }

  /**
   * Sorts the commits that are reachable from the base.
   * The base cannot bound this walk, so the common ancestor of the group bounds it.
   * The common ancestor itself can be a member of the group; it then goes first.
   */
  private fun sortReachableParentsFirst(group: List<VcsFullCommitDetails>): List<VcsFullCommitDetails> {
    if (group.size < 2) return group

    val byHash = group.associateBy { it.id.asString() }
    val mergeBase = findOctopusMergeBase(byHash.keys) ?: return group
    val childrenFirst = revListChildrenFirst(byHash.keys, boundary = mergeBase).mapNotNull { byHash[it] }
    return listOfNotNull(byHash[mergeBase]) + childrenFirst.asReversed()
  }

  private fun revListChildrenFirst(hashes: Collection<String>, boundary: String): List<String> {
    val handler = GitLineHandler(project, repository.root, GitCommand.REV_LIST)
    handler.addParameters("--topo-order")
    handler.addParameters(hashes.toList())
    handler.addParameters("--not", boundary)
    val result = Git.getInstance().runCommand(handler)
    result.throwOnError()
    return result.output.map { it.trim() }
  }

  private fun findOctopusMergeBase(hashes: Collection<String>): String? {
    val handler = GitLineHandler(project, repository.root, GitCommand.MERGE_BASE)
    handler.addParameters("--octopus")
    handler.addParameters(hashes.toList())
    val result = Git.getInstance().runCommand(handler)
    if (!result.success()) return null
    return result.output.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
  }

  private fun showPushDialog(newOid: Oid) {
    // Use a detached push source with the new commit OID
    val pushSource = GitPushSource.createDetached(newOid.hex())

    val dialog = VcsPushDialog(
      project,
      listOf(repository),
      listOf(repository),
      repository,
      pushSource,
      GitPushTarget(remoteBranch, false, GitPushTargetType.CUSTOM),
    )
    DialogManager.show(dialog)
  }

  private fun handleMergeConflict(e: MergeConflictException) {
    val message = GitBundle.message(
      "notification.content.add.to.remote.branch.conflict",
      remoteBranch.nameForLocalOperations,
      e.description,
    )
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.ADD_COMMIT_TO_REMOTE_BRANCH_CONFLICT,
      GitBundle.message("notification.title.add.to.remote.branch.conflict"),
      message,
    )
    showInVcsConsole(message)
  }

  private fun handleError(e: VcsException) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.ADD_COMMIT_TO_REMOTE_BRANCH_FAILED,
      GitBundle.message("notification.title.add.to.remote.branch.failed"),
      e.message,
    )
    showInVcsConsole(e.message)
  }

  private fun showInVcsConsole(message: @Nls String) {
    val console = VcsConsoleTabService.getInstance(project)
    console.addMessage(message, ConsoleViewContentType.ERROR_OUTPUT)
    console.showConsoleTabAndScrollToTheEnd()
  }

  private fun notifyNothingToAdd() {
    VcsNotifier.getInstance(project).notifyInfo(
      GitNotificationIdsHolder.ADD_COMMIT_TO_REMOTE_BRANCH_NOTHING_TO_DO,
      GitBundle.message("notification.title.add.to.remote.branch.nothing.to.do"),
      GitBundle.message(
        "notification.content.add.to.remote.branch.nothing.to.do",
        remoteBranch.nameForLocalOperations,
      ),
    )
  }
}
