// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.addCommit

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.DialogManager
import git4idea.GitNotificationIdsHolder
import git4idea.GitRemoteBranch
import git4idea.commands.Git
import git4idea.fetch.GitFetchSpec
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.MergeConflictException
import git4idea.inMemory.objects.Oid
import git4idea.inMemory.rebaseCommit
import git4idea.push.GitPushSource
import git4idea.push.GitPushTarget
import git4idea.push.GitPushTargetType
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val LOG = logger<GitAddCommitToRemoteBranchOperation>()

internal class GitAddCommitToRemoteBranchOperation(
  private val project: Project,
  private val repository: GitRepository,
  private val commits: List<VcsFullCommitDetails>,
  private val remoteBranch: GitRemoteBranch,
  private val cs: CoroutineScope,
) {
  suspend fun execute() {
    withBackgroundProgress(project, title = GitBundle.message("progress.title.adding.commits.to.remote.branch", commits.size, remoteBranch.nameForRemoteOperations), cancellable = true) {
      reportProgressScope(size = 2) { reporter ->
        try {
          reporter.indeterminateStep(GitBundle.message("progress.text.fetching.remote.branch")) {
            fetchRemoteBranch()
          }

          val newRemoteBranchOid = reporter.itemStep(GitBundle.message("progress.text.cherry.picking.commits")) {
            cherryPickCommitsInMemory()
          }

          cs.launch(Dispatchers.EDT) {
            showPushDialog(newRemoteBranchOid)
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

  private fun cherryPickCommitsInMemory(): Oid {
    val objectRepo = GitObjectRepository(repository)

    // Get the current remote branch tip
    val remote = remoteBranch.remote
    val branchName = remoteBranch.nameForRemoteOperations
    val remoteBranchRef = "refs/remotes/${remote.name}/$branchName"

    val remoteTipHash = Git.getInstance().resolveReference(repository, remoteBranchRef)
      ?: throw VcsException(GitBundle.message("error.cannot.resolve.reference", remoteBranchRef))

    LOG.info("Remote branch tip: $remoteTipHash")

    var currentBase = objectRepo.findCommit(Oid.fromHex(remoteTipHash.asString()))

    // Cherry-pick each commit sequentially
    for (commitDetails in commits) {
      val commitOid = Oid.fromHex(commitDetails.id.asString())
      val commit = objectRepo.findCommit(commitOid)

      LOG.info("Cherry-picking commit ${commit.oid} onto ${currentBase.oid}")

      val newCommitOid = objectRepo.rebaseCommit(commit, currentBase)
      currentBase = objectRepo.findCommit(newCommitOid)

      LOG.info("Created new commit: ${currentBase.oid}")
    }

    return currentBase.oid
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
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.ADD_COMMIT_TO_REMOTE_BRANCH_CONFLICT,
      GitBundle.message("notification.title.add.to.remote.branch.conflict"),
      GitBundle.message(
        "notification.content.add.to.remote.branch.conflict",
        remoteBranch.nameForLocalOperations,
        e.description,
      ),
    )
  }

  private fun handleError(e: VcsException) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.ADD_COMMIT_TO_REMOTE_BRANCH_FAILED,
      GitBundle.message("notification.title.add.to.remote.branch.failed"),
      e.message,
    )
  }
}
