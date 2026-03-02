// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.dvcs.repo.isHead
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.commit.CommitExceptionWithActions
import com.intellij.vcs.log.Hash
import git4idea.GitDisposable
import git4idea.GitUtil
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.inMemory.rebase.log.InMemoryRebaseOperations
import git4idea.inMemory.rebase.log.RebaseEntriesSource
import git4idea.rebase.GitSquashedCommitsMessage
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitInteractiveRebaseEntriesProvider
import git4idea.rebase.log.GitRebaseEntryGeneratedUsingLog
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.reset.GitResetMode
import git4idea.reset.GitResetOperation
import kotlinx.coroutines.launch

internal object GitAmendSpecificCommitSquasher {
  /**
   * Squashes the "amend!" commit that should be the current HEAD into the target commit
   * If squash fails, the "amend!" commit is undone
   */
  fun squashAmendCommitIntoTarget(repository: GitRepository, target: Hash, newMessage: String) {
    val amendCommit = GitLogUtil.collectMetadataForCommit(repository.project, repository.root, GitUtil.HEAD) ?: error("No HEAD commit")
    val targetCommit = GitLogUtil.collectMetadataForCommit(repository.project, repository.root, target.asString()) ?: error("No target commit")

    check(GitSquashedCommitsMessage.canAutosquash(amendCommit.fullMessage, setOf(targetCommit.subject)))

    runBlockingCancellable {
      val entries = repository.project.service<GitInteractiveRebaseEntriesProvider>().tryGetEntriesUsingLog(repository, targetCommit)
        ?.plus(GitRebaseEntryGeneratedUsingLog(amendCommit))
      checkNotNull(entries)

      val result =
        InMemoryRebaseOperations.squash(repository, listOf(amendCommit, targetCommit), newMessage, RebaseEntriesSource.Entries(entries))

      val amendCommitParent = amendCommit.parents.single()
      when (result) {
        is GitCommitEditingOperationResult.Complete -> return@runBlockingCancellable
        is GitCommitEditingOperationResult.Conflict -> {
          undoAmendCommit(repository, amendCommit.id, amendCommitParent)
          throw AmendSpecificCommitConflictException(repository, amendCommit.id, amendCommitParent)
        }
        is GitCommitEditingOperationResult.Incomplete -> {
          undoAmendCommit(repository, amendCommit.id, amendCommitParent)
          throw VcsException(GitBundle.message("git.commit.amend.specific.commit.error.message"))
        }
      }
    }
  }

  private suspend fun undoAmendCommit(repository: GitRepository, amendCommit: Hash, amendCommitParent: Hash) {
    if (!repository.isHead(amendCommit)) return
    // try to reset, don't report on fail
    coroutineToIndicator { indicator ->
      GitResetOperation(repository.project,
                        mapOf(repository to amendCommitParent),
                        GitResetMode.MIXED,
                        indicator,
                        GitResetOperation.SmartResetPolicy.FAIL).execute(false)
    }
    repository.update()
  }

  class AmendSpecificCommitConflictException(
    private val repository: GitRepository,
    private val amendCommit: Hash,
    private val amendCommitParent: Hash,
  ) : VcsException(GitBundle.message("git.commit.amend.specific.commit.merge.conflict.error.message")), CommitExceptionWithActions {
    override fun getActions(notification: Notification): List<NotificationAction> {
      val connection = repository.project.messageBus.connect()
      notification.whenExpired { connection.disconnect() }
      connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repo ->
        if (repo == repository) {
          if (!canReset()) {
            notification.expire()
          }
        }
      })

      return listOf(NotificationAction.createSimpleExpiring(GitBundle.message("git.commit.amend.specific.commit.merge.conflict.create.anyway.text")) {
        GitDisposable.getInstance(repository.project).coroutineScope.launch {
          if (!canReset()) {
            return@launch
          }
          resetToAmendCommit()
        }
      })
    }

    override val shouldAddShowDetailsAction: Boolean = false

    private fun canReset(): Boolean {
      repository.update()
      return repository.isHead(amendCommitParent)
    }

    suspend fun resetToAmendCommit() {
      val presentation = GitResetOperation.OperationPresentation().apply {
        notificationSuccess = "git.commit.amend.specific.commit.reset.successful.notification.message"
        notificationFailure = "git.commit.amend.specific.commit.reset.failed.notification.title"
      }
      withBackgroundProgress(repository.project, GitBundle.message(presentation.activityName)) {
        coroutineToIndicator { indicator ->
          GitResetOperation(repository.project,
                            mapOf(repository to amendCommit.asString()),
                            GitResetMode.MIXED,
                            indicator,
                            presentation,
                            GitResetOperation.SmartResetPolicy.FAIL).execute()
        }
      }
    }
  }
}
