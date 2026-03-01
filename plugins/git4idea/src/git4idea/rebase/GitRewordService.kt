// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.rebase.log.reword.GitInMemoryRewordOperation
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.executeInMemoryWithFallback
import git4idea.rebase.log.notifySuccess
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Service(Service.Level.PROJECT)
internal class GitRewordService(private val project: Project, private val cs: CoroutineScope) {
  fun launchRewordForHeads(forRepositories: List<GitRepository>) {
    val currentRevByRepository = forRepositories.associateWith { it.currentRevision }
    cs.launch {
      withBackgroundProgress(project, GitBundle.message("rebase.log.reword.action.progress.indicator.title")) {
        val spec = withContext(Dispatchers.IO) {
          currentRevByRepository.mapNotNull { (repository, hash) ->
            checkCanceled()
            val details = coroutineToIndicator {
              GitHistoryUtils.collectCommitsMetadata(repository.project, repository.root, hash)?.singleOrNull()
            }
            details?.let { RewordSpec(repository, hash, details) }
          }
        }

        val newMessage = showDialogForMessage(project, spec)
        val rewordResults = spec.map { (repository, _, metadata) ->
          executeRewordOperation(repository, metadata, newMessage)
        }
        rewordResults.filterIsInstance<GitCommitEditingOperationResult.Complete>().notifyRewordSuccess(editAgain = {
          launchRewordForHeads(forRepositories)
        })
      }
    }
  }

  // Called for a specific commit from the git log
  fun launchReword(repository: GitRepository, commit: VcsCommitMetadata, newMessage: String) {
    val isHead = commit.id == HashImpl.build(repository.currentRevision!!)
    cs.launch {
      val operationResult = withBackgroundProgress(project, GitBundle.message("rebase.log.reword.action.progress.indicator.title")) {
        executeRewordOperation(repository, commit, newMessage)
      }
      if (operationResult is GitCommitEditingOperationResult.Complete) {
        operationResult.notifyRewordSuccess(editAgain = {
          repository.project.service<GitRewordService>().launchRewordForHeads(listOf(repository))
        }.takeIf { isHead })
        ChangeListManagerImpl.getInstanceImpl(project).replaceCommitMessage(commit.fullMessage, newMessage)
      }
    }
  }
}

private suspend fun executeRewordOperation(
  repository: GitRepository,
  commit: VcsCommitMetadata,
  newMessage: String,
): GitCommitEditingOperationResult {
  return withContext(Dispatchers.IO) {
    executeInMemoryWithFallback(
      inMemoryOperation = {
        val objectRepo = GitObjectRepository(repository)
        val showFailureNotification = Registry.`is`("git.in.memory.interactive.rebase.debug.notify.errors")
        GitInMemoryRewordOperation(objectRepo, commit.id, newMessage).execute(showFailureNotification)
      },
      fallbackOperation = {
        coroutineToIndicator {
          GitRewordOperation(repository, commit, newMessage).execute()
        }
      }
    )
  }
}

private suspend fun showDialogForMessage(project: Project, specs: Collection<RewordSpec>): String {
  val logData = VcsProjectLog.awaitLogIsReady(project)?.dataManager ?: error("Git Log is not ready")
  return withContext(Dispatchers.UiWithModelAccess) {
    val dialog = GitNewCommitMessageActionDialog(project = project,
                                                 originMessage = specs.first().commit.fullMessage,
                                                 selectedChanges = null,
                                                 validateCommitEditable = { validateRewordable(logData, specs) },
                                                 title = GitBundle.message("rebase.log.reword.dialog.title"),
                                                 dialogLabel = buildRewordDialogLabel(specs))
    suspendCancellableCoroutine { cont ->
      dialog.show(onOk = { if (cont.isActive) cont.resume(it) }, onClose = {
        if (cont.isActive) {
          cont.resumeWithException(CancellationException("Dialog closed"))
        }
      })

      cont.invokeOnCancellation {
        runInEdt(ModalityState.any()) { dialog.close(CANCEL_EXIT_CODE) }
      }
    }
  }
}

private fun validateRewordable(logData: VcsLogData, spec: Collection<RewordSpec>): ValidationInfo? =
  spec.firstNotNullOfOrNull { (repository, lastKnownRevision, commit) ->
    GitNewCommitMessageActionDialog.validateCommitsEditable(logData, repository, listOf(commit.id), lastKnownRevision)
  }

private fun buildRewordDialogLabel(spec: Collection<RewordSpec>): @Nls String {
  // We're rewording only if the commits are the latest commits we've just done, so they're guaranteed to be by the same author
  val commits = spec.map { it.commit }
  val author = commits.first().author.let { getShortPresentation(it) }
  val commitHashes = commits.joinToString(separator = ", ") { commit -> commit.id.toShortString() }
  return GitBundle.message("rebase.log.reword.dialog.description.label", commits.size, commitHashes, author)
}

private fun GitCommitEditingOperationResult.Complete.notifyRewordSuccess(editAgain: (() -> Unit)?) =
  listOf(this).notifyRewordSuccess(editAgain)

private fun List<GitCommitEditingOperationResult.Complete>.notifyRewordSuccess(editAgain: (() -> Unit)?) = notifySuccess(
  GitBundle.message("rebase.log.reword.action.notification.successful.title"),
  null,
  GitBundle.message("rebase.log.reword.action.progress.indicator.undo.title"),
  GitBundle.message("rebase.log.reword.action.notification.undo.not.allowed.title"),
  GitBundle.message("rebase.log.reword.action.notification.undo.failed.title"),
  editAgain = editAgain
)

private data class RewordSpec(val repository: GitRepository, val lastKnownRevision: String?, val commit: VcsCommitMetadata)
