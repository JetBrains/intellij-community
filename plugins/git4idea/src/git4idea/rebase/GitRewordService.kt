// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
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
import com.intellij.vcs.log.Hash
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

@Service(Service.Level.PROJECT)
internal class GitRewordService(private val project: Project, private val cs: CoroutineScope) {
  fun launchReword(repoWithCommitHash: Map<GitRepository, Hash>) {
    // read immediately to compare later
    val currentRevByRepository = repoWithCommitHash.keys.associateWith { it.currentRevision }
    cs.launch {
      withBackgroundProgress(project, GitBundle.message("rebase.log.reword.action.progress.indicator.title")) {
        val spec = withContext(Dispatchers.IO) {
          repoWithCommitHash.mapNotNull { (repository, hash) ->
            checkCanceled()
            val details = coroutineToIndicator {
              GitHistoryUtils.collectCommitsMetadata(repository.project, repository.root, hash.asString())?.singleOrNull()
            }
            details?.let { RewordSpec(repository, currentRevByRepository[repository], details) }
          }
        }

        val newMessage = showDialogForMessage(project, spec)
        val rewordResults = spec.map { (repository, _, metadata) ->
          executeRewordOperation(repository, metadata, newMessage)
        }

        rewordResults.filterIsInstance<GitCommitEditingOperationResult.Complete>().notifyRewordSuccess(editAgain = {
          val newHashes =
            spec.mapNotNull { (repository, _, _) -> repository.currentRevision?.let { repository to HashImpl.build(it) } }.toMap()
          launchReword(newHashes)
        })
      }
    }
  }

  fun launchReword(repository: GitRepository, commit: VcsCommitMetadata, newMessage: String) {
    cs.launch {
      val operationResult =
        withBackgroundProgress(project, GitBundle.message("rebase.log.reword.action.progress.indicator.title")) {
          executeRewordOperation(repository, commit, newMessage)
        }
      if (operationResult is GitCommitEditingOperationResult.Complete) {
        operationResult.notifyRewordSuccess()
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
        val showFailureNotification = Registry.`is`("git.in.memory.interactive.rebase.notify.errors")
        GitInMemoryRewordOperation(objectRepo, commit, newMessage).execute(showFailureNotification)
      },
      fallbackOperation = {
        coroutineToIndicator {
          GitRewordOperation(repository, commit, newMessage).execute()
        }
      }
    )
  }
}

private suspend fun showDialogForMessage(project: Project, spec: Collection<RewordSpec>): String {
  val logData = VcsProjectLog.awaitLogIsReady(project)?.dataManager ?: error("Git Log is not ready")
  return withContext(Dispatchers.EDT) {
    val dialog = GitNewCommitMessageActionDialog(project = project,
                                                 originMessage = spec.first().commit.fullMessage,
                                                 selectedChanges = null,
                                                 validateCommitEditable = { validateRewordable(logData, spec) },
                                                 title = GitBundle.message("rebase.log.reword.dialog.title"),
                                                 dialogLabel = buildRewordDialogLabel(spec))
    suspendCancellableCoroutine { cont ->
      dialog.show {
        cont.resume(it) { _, _, _ ->
          runInEdt(ModalityState.any()) { dialog.close(CANCEL_EXIT_CODE) }
        }
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

private fun GitCommitEditingOperationResult.Complete.notifyRewordSuccess() {
  val newHashes = repository.currentRevision?.let {
    mapOf(repository to HashImpl.build(it))
  }
  val editAgainAction = newHashes?.let {
    { repository.project.service<GitRewordService>().launchReword(it) }
  }
  listOf(this).notifySuccess(
    GitBundle.message("rebase.log.reword.action.notification.successful.title"),
    null,
    GitBundle.message("rebase.log.reword.action.progress.indicator.undo.title"),
    GitBundle.message("rebase.log.reword.action.notification.undo.not.allowed.title"),
    GitBundle.message("rebase.log.reword.action.notification.undo.failed.title"),
    editAgain = editAgainAction
  )
}

private fun List<GitCommitEditingOperationResult.Complete>.notifyRewordSuccess(editAgain: () -> Unit) = notifySuccess(
  GitBundle.message("rebase.log.reword.action.notification.successful.title"),
  null,
  GitBundle.message("rebase.log.reword.action.progress.indicator.undo.title"),
  GitBundle.message("rebase.log.reword.action.notification.undo.not.allowed.title"),
  GitBundle.message("rebase.log.reword.action.notification.undo.failed.title"),
  editAgain = editAgain
)

private data class RewordSpec(val repository: GitRepository, val lastKnownRevision: String?, val commit: VcsCommitMetadata)
