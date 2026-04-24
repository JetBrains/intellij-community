// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log

import com.intellij.dvcs.repo.isHead
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.findCommitsRange
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid
import git4idea.inMemory.objects.toHash
import git4idea.rebase.interactive.getRebaseUpstreamFor
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.reset.GitResetMode
import git4idea.util.GitPreservingProcess
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

internal abstract class GitInMemoryCommitEditingOperation(
  protected val objectRepo: GitObjectRepository,
  private val baseCommit: Hash,
) {
  protected abstract suspend fun editCommits(): CommitEditingResult

  protected abstract val operationName: @Nls String
  protected abstract val failureTitle: @NonNls String

  protected lateinit var initialHeadPosition: Hash

  /**
   * A linear range of commits that is being edited is loaded into memory
   */
  protected val baseToHeadCommitsRange: List<GitObject.Commit> by lazy {
    objectRepo.findCommitsRange(baseCommit, initialHeadPosition)
  }

  suspend fun execute(showFailureNotification: Boolean = true): GitCommitEditingOperationResult {
    objectRepo.repository.update()
    initialHeadPosition = HashImpl.build(objectRepo.repository.currentRevision!!)

    try {
      val result = editCommits()
      assertCurrentRevMatchesInitialHead()

      if (result.requiresWorkingTreeUpdate) {
        resetToNewHead(result.newHead)
      }
      else {
        updateRefToNewHead(result.newHead)
      }

      objectRepo.repository.update()
      val upstream = getRebaseUpstreamFor(baseToHeadCommitsRange.first())

      return GitCommitEditingOperationResult.Complete(objectRepo.repository,
                                                      upstream,
                                                      initialHeadPosition.asString(),
                                                      result.newHead.hex(),
                                                      result.commitToFocus?.toHash(),
                                                      result.commitToFocusOnUndo?.toHash())
    }
    catch (e: VcsException) {
      if (showFailureNotification) notifyOperationFailed(e)
      LOG.warn("Failed to execute in-memory rebase operation", e)
      return GitCommitEditingOperationResult.Incomplete
    }
  }

  private fun updateRefToNewHead(newHead: Oid) {
    GitUtil.updateHeadReference(objectRepo.repository,
                                newHead.toHash(),
                                fullReflogMessage)
  }

  /**
   * Both index and working tree are updated on files that are different between current and new head
   * Local changes are saved before reset and then restored
   */
  private suspend fun resetToNewHead(newHead: Oid) {
    val destinationName = objectRepo.repository.currentBranchName ?: newHead.hex()
    GitPreservingProcess.runWithPreservedLocalChanges(objectRepo.repository, operationName, destinationName) {
      Git.getInstance().reset(objectRepo.repository,
                          GitResetMode.KEEP,
                          newHead.hex(),
                          fullReflogMessage).throwOnError()
      GitUtil.refreshChangedVfs(objectRepo.repository, initialHeadPosition)
    }
  }

  private val fullReflogMessage
    get() = "$operationName $REFLOG_MESSAGE_SUFFIX"

  private fun notifyOperationFailed(exception: VcsException) {
    VcsNotifier.getInstance(objectRepo.repository.project).notifyError(
      GitNotificationIdsHolder.IN_MEMORY_OPERATION_FAILED,
      failureTitle,
      exception.message
    )
  }

  protected fun assertCurrentRevMatchesInitialHead(performUpdate: Boolean = true) {
    if (performUpdate) {
      objectRepo.repository.update()
    }

    if (!objectRepo.repository.isHead(initialHeadPosition)) {
      throw VcsException(GitBundle.message("in.memory.rebase.fail.head.move"))
    }
  }

  protected data class CommitEditingResult(
    val newHead: Oid,
    val requiresWorkingTreeUpdate: Boolean,
    val commitToFocus: Oid? = null,
    val commitToFocusOnUndo: Oid? = null,
  )

  companion object {
    @NonNls
    private val REFLOG_MESSAGE_SUFFIX = "by ${ApplicationNamesInfo.getInstance().fullProductName} Git plugin"
    private val LOG = logger<GitInMemoryCommitEditingOperation>()
  }
}