// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.findCommitsRange
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid
import git4idea.inMemory.objects.toHash
import git4idea.rebase.interactive.getRebaseUpstreamFor
import git4idea.rebase.log.GitCommitEditingOperationResult
import org.jetbrains.annotations.NonNls

internal abstract class GitInMemoryCommitEditingOperation(
  protected val objectRepo: GitObjectRepository,
  private val baseCommitMetadata: VcsCommitMetadata,
) {
  companion object {
    @NonNls
    private val REFLOG_MESSAGE_SUFFIX = "by ${ApplicationNamesInfo.getInstance().fullProductName} Git plugin"
  }

  protected lateinit var initialHeadPosition: String

  /**
   * A linear range of commits that is being edited is loaded into memory
   */
  protected val baseToHeadCommitsRange: List<GitObject.Commit> by lazy {
    objectRepo.findCommitsRange(baseCommitMetadata.id.asString(), initialHeadPosition)
  }

  suspend fun execute(showFailureNotification: Boolean = true): GitCommitEditingOperationResult {
    objectRepo.repository.update()
    initialHeadPosition = objectRepo.repository.currentRevision!!

    try {
      val result = editCommits()
      assertCurrentRevMatchesInitialHead()
      GitUtil.updateHead(objectRepo.repository,
                         result.newHead.toHash(),
                         "$reflogMessage $REFLOG_MESSAGE_SUFFIX")
      objectRepo.repository.update()
      val upstream = getRebaseUpstreamFor(baseCommitMetadata)

      return GitCommitEditingOperationResult.Complete(objectRepo.repository, upstream, initialHeadPosition,
                                                      result.newHead.hex(), result.commitToFocus?.toHash(), result.commitToFocusOnUndo?.toHash())
    }
    catch (e: VcsException) {
      if (showFailureNotification) notifyOperationFailed(e)
      return GitCommitEditingOperationResult.Incomplete
    }
  }

  protected abstract suspend fun editCommits(): CommitEditingResult

  protected abstract val reflogMessage: @NonNls String
  protected abstract val failureTitle: @NonNls String

  private fun notifyOperationFailed(exception: VcsException) {
    VcsNotifier.getInstance(objectRepo.repository.project).notifyError(
      GitNotificationIdsHolder.IN_MEMORY_OPERATION_FAILED,
      failureTitle,
      exception.message
    )
  }

  protected data class CommitEditingResult(
    val newHead: Oid,
    val commitToFocus: Oid? = null,
    val commitToFocusOnUndo: Oid? = null,
  )

  protected fun assertCurrentRevMatchesInitialHead(performUpdate: Boolean = true) {
    if (performUpdate) {
      objectRepo.repository.update()
    }

    if (objectRepo.repository.currentRevision!! != initialHeadPosition) {
      throw VcsException(GitBundle.message("in.memory.rebase.fail.head.move"))
    }
  }
}