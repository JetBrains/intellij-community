// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcs.commit.CommitExceptionWithActions
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.inMemory.rebase.log.InMemoryRebaseOperations
import git4idea.inMemory.rebase.log.RebaseEntriesSource
import git4idea.rebase.GitSquashedCommitsMessage
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitInteractiveRebaseEntriesProvider
import git4idea.rebase.log.GitRebaseEntryGeneratedUsingLog
import git4idea.repo.GitRepository
import git4idea.reset.GitResetMode

internal object GitAmendSpecificCommitSquasher {
  /**
   * Squashes the "amend!" commit that should be the current HEAD into the target commit
   * If squash fails, the "amend!" commit is undone
   */
  fun squashAmendCommitIntoTarget(repository: GitRepository, target: Hash, newMessage: String) {
    val amendCommit = GitLogUtil.collectMetadata(repository.project, repository.root, listOf(GitUtil.HEAD)).single()
    val targetCommit = GitLogUtil.collectMetadata(repository.project, repository.root, listOf(target.asString())).single()

    check(GitSquashedCommitsMessage.canAutosquash(amendCommit.fullMessage, setOf(targetCommit.subject)))

    runBlockingCancellable {
      val entries = repository.project.service<GitInteractiveRebaseEntriesProvider>()
        .tryGetEntriesUsingLog(repository, targetCommit)?.plus(GitRebaseEntryGeneratedUsingLog(amendCommit))
      checkNotNull(entries)

      val result =
        InMemoryRebaseOperations.squash(repository, listOf(amendCommit, targetCommit), newMessage, RebaseEntriesSource.Entries(entries))

      when (result) {
        is GitCommitEditingOperationResult.Complete -> return@runBlockingCancellable
        is GitCommitEditingOperationResult.Conflict -> {
          undoAmendCommit(repository, amendCommit)
          throw AmendSpecificCommitConflictException()
        }
        is GitCommitEditingOperationResult.Incomplete -> {
          undoAmendCommit(repository, amendCommit)
          throw VcsException(GitBundle.message("git.commit.amend.specific.commit.error.message"))
        }
      }
    }
  }

  private fun undoAmendCommit(repository: GitRepository, amendCommit: VcsCommitMetadata) {
    if (repository.currentRevision!! != amendCommit.id.asString()) return
    val amendCommitParent = amendCommit.parents.single()
    // try to reset, don't report on fail
    Git.getInstance().reset(repository, GitResetMode.MIXED, amendCommitParent.asString())
  }

  class AmendSpecificCommitConflictException :
    VcsException(GitBundle.message("git.commit.amend.specific.commit.merge.conflict.error.message")), CommitExceptionWithActions {
    override val actions: List<NotificationAction> = listOf()
    override val shouldAddShowDetailsAction: Boolean = false
  }
}