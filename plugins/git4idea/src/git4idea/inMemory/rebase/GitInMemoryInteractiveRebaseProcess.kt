// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitNotificationIdsHolder
import git4idea.GitOperationsCollector
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.MergeConflictException
import git4idea.inMemory.mergeTrees
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.rebase.log.GitInMemoryCommitEditingOperation
import git4idea.inMemory.rebaseCommit
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.interactive.GitRebaseTodoModel
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.notifySuccess
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * Performs an interactive rebase without modifying the working directory and index.
 * Supports pick, reword, fixup, and drop operations on a linear sequence of commits.
 * @throws MergeConflictException if rebase fails due to merge conflicts.
 */
internal class GitInMemoryInteractiveRebaseProcess(
  objectRepo: GitObjectRepository,
  private val rebaseData: GitInMemoryRebaseData,
) : GitInMemoryCommitEditingOperation(objectRepo, rebaseData.baseCommit) {

  override val operationName: @Nls String = GitBundle.message("action.Git.Interactive.Rebase.operation.name")
  override val failureTitle: @NonNls String = GitBundle.message("in.memory.rebase.log.interactive.failed.title")

  override suspend fun editCommits(): CommitEditingResult {
    if (initialHeadPosition != rebaseData.initialHead) {
      throw VcsException(GitBundle.message("in.memory.rebase.fail.head.move"))
    }

    var baseCommit = baseToHeadCommitsRange.first().parentsOids.singleOrNull()?.let { objectRepo.findCommit(it) }

    val entriesWithCommits = rebaseData.entries.map { entry ->
      val commit = baseToHeadCommitsRange.find { it.oid.hex() == entry.commit }
                   ?: error("Couldn't find a commit corresponding to entry $entry")
      entry to commit
    }

    reportSequentialProgress(entriesWithCommits.size) { reporter ->
      entriesWithCommits.forEach { (entry, commitToRebase) ->
        baseCommit = processEntry(baseCommit, entry, commitToRebase)
        reporter.itemStep()
      }
    }

    if (baseCommit == null) { // all commits are dropped
      // To have the same behavior as regular interactive rebase action, creates empty commit
      baseCommit = objectRepo.findCommit(objectRepo.commitTree(objectRepo.emptyTree.oid, listOf(), byteArrayOf()))
    }

    val modifiesTree = baseCommit.treeOid != baseToHeadCommitsRange.last().treeOid
    return CommitEditingResult(baseCommit.oid, requiresWorkingTreeUpdate = modifiesTree)
  }

  private fun processEntry(
    baseCommit: GitObject.Commit?,
    entry: GitRebaseEntry,
    commitToRebase: GitObject.Commit,
  ): GitObject.Commit? {
    val processor = actionProcessors[entry.action::class]
                    ?: throw UnsupportedOperationException("Action ${entry.action} not supported in memory rebase")
    return processor.process(objectRepo, baseCommit, commitToRebase, entry)
  }

  companion object {
    private val actionProcessors = mapOf<KClass<out GitRebaseEntry.Action>, RebaseActionProcessor>(
      GitRebaseEntry.Action.PICK::class to PickActionProcessor,
      GitRebaseEntry.Action.REWORD::class to RewordActionProcessor,
      GitRebaseEntry.Action.FIXUP::class to FixupActionProcessor,
      GitRebaseEntry.Action.DROP::class to DropActionProcessor
    )

    val SUPPORTED_ACTIONS = actionProcessors.keys

    private object PickActionProcessor : RebaseActionProcessor {
      override fun process(objectRepo: GitObjectRepository, baseCommit: GitObject.Commit?, commitToRebase: GitObject.Commit, entry: GitRebaseEntry): GitObject.Commit {
        if (commitToRebase.parentsOids.singleOrNull() == baseCommit?.oid) {
          return commitToRebase
        }
        return objectRepo.findCommit(objectRepo.rebaseCommit(commitToRebase, baseCommit))
      }
    }

    private object RewordActionProcessor : RebaseActionProcessor {
      override fun process(objectRepo: GitObjectRepository, baseCommit: GitObject.Commit?, commitToRebase: GitObject.Commit, entry: GitRebaseEntry): GitObject.Commit {
        val newMessage = (entry as GitRebaseRewordEntryWithMessage).newMessage
        val rewordedCommit = objectRepo.commitTreeWithOverrides(commitToRebase, message = newMessage.toByteArray())
        return objectRepo.findCommit(objectRepo.rebaseCommit(objectRepo.findCommit(rewordedCommit), baseCommit))
      }
    }

    private object FixupActionProcessor : RebaseActionProcessor {
      override fun process(objectRepo: GitObjectRepository, baseCommit: GitObject.Commit?, commitToRebase: GitObject.Commit, entry: GitRebaseEntry): GitObject.Commit {
        checkNotNull(baseCommit) { "Can't apply squash as first commit" }
        val mergedTree = objectRepo.mergeTrees(commitToRebase, baseCommit)
        objectRepo.persistObject(mergedTree)

        val resultCommitOid = if ((entry.action as GitRebaseEntry.Action.FIXUP).overrideMessage) {
          objectRepo.commitTreeWithOverrides(baseCommit, treeOid = mergedTree.oid, message = commitToRebase.message)
        }
        else {
          objectRepo.commitTreeWithOverrides(baseCommit, treeOid = mergedTree.oid)
        }
        return objectRepo.findCommit(resultCommitOid)
      }
    }

    private object DropActionProcessor : RebaseActionProcessor {
      override fun process(objectRepo: GitObjectRepository, baseCommit: GitObject.Commit?, commitToRebase: GitObject.Commit, entry: GitRebaseEntry): GitObject.Commit? {
        return baseCommit
      }
    }
  }

  /**
   * Implementations should cover rebase actions [GitRebaseEntry.Action]
   * They should apply the action with respective commit and return the new top of the commit sequence.
   * All created objects must be persisted to the repository.
   */
  private interface RebaseActionProcessor {
    /**
     * @param baseCommit Top of the current commit sequence being built (null if a sequence is empty)
     * @param commitToRebase The commit being processed
     * @param entry Details for the rebase operation
     * @return New top commit after processing. It should be persisted to the repository.
     */
    fun process(
      objectRepo: GitObjectRepository,
      baseCommit: GitObject.Commit?,
      commitToRebase: GitObject.Commit,
      entry: GitRebaseEntry,
    ): GitObject.Commit?
  }
}


/**
 * Main entry point for performing an in-memory interactive rebase.
 * In-memory rebase doesn't support stops (in this case we would have to modify the working directory).
 * Thus, it fails if we have a merge conflict or doesn't start if we have an EDIT entry.
 * Optionally can show a success notification to the user.
 * There is a setting whether to show a failure to the user
 */
internal suspend fun performInMemoryRebase(
  objectRepo: GitObjectRepository,
  entries: List<GitRebaseEntry>,
  model: GitRebaseTodoModel<out GitRebaseEntry>,
  notifySuccess: Boolean = true,
): GitCommitEditingOperationResult {
  if (!isInMemoryRebaseSupported(objectRepo.repository)) {
    return GitCommitEditingOperationResult.Incomplete
  }
  val showFailureNotification = Registry.`is`("git.in.memory.interactive.rebase.debug.notify.errors")

  val rebaseData = createRebaseData(model, entries, objectRepo.repository, showFailureNotification)
                   ?: return GitCommitEditingOperationResult.Incomplete
  val rebaseActivity = GitOperationsCollector.startInMemoryInteractiveRebase(objectRepo.repository.project)
  val operationResult = executeRebase(objectRepo, rebaseData, showFailureNotification, rebaseActivity)

  when (operationResult) {
    is GitCommitEditingOperationResult.Complete -> {
      GitOperationsCollector.endInMemoryInteractiveRebase(rebaseActivity, InMemoryRebaseResult.SUCCESS)
      if (notifySuccess) {
        operationResult.notifySuccess(
          GitBundle.message("in.memory.rebase.log.interactive.action.notification.successful"),
          null,
          GitBundle.message("in.memory.rebase.log.interactive.action.progress.indicator.undo.title"),
          GitBundle.message("in.memory.rebase.log.interactive.action.notification.undo.not.allowed.title"),
          GitBundle.message("in.memory.rebase.log.interactive.action.notification.undo.failed.title")
        )
      }
    }
    is GitCommitEditingOperationResult.Conflict ->
      GitOperationsCollector.endInMemoryInteractiveRebase(rebaseActivity, InMemoryRebaseResult.CONFLICT)
    is GitCommitEditingOperationResult.Incomplete ->
      GitOperationsCollector.endInMemoryInteractiveRebase(rebaseActivity, InMemoryRebaseResult.ERROR)
  }
  return operationResult
}

private fun isInMemoryRebaseSupported(repository: GitRepository): Boolean {
  return GitVersionSpecialty.MERGE_TREE_MERGE_BASE_OPTION_SUPPORTED.existsIn(repository)
}

private fun createRebaseData(
  model: GitRebaseTodoModel<out GitRebaseEntry>,
  entries: List<GitRebaseEntry>,
  repository: GitRepository,
  showFailureNotification: Boolean,
): GitInMemoryRebaseData? {
  val validationResult = GitInMemoryRebaseData.createValidatedRebaseData(
    model,
    HashImpl.build(entries.first().commit),
    HashImpl.build(entries.last().commit)
  )

  return when (validationResult) {
    is GitInMemoryRebaseData.Companion.ValidationResult.Valid -> validationResult.rebaseData
    is GitInMemoryRebaseData.Companion.ValidationResult.Invalid -> {
      if (showFailureNotification) {
        notifyValidationFailure(repository, validationResult.reason)
      }
      null
    }
  }
}

private suspend fun executeRebase(
  objectRepo: GitObjectRepository,
  rebaseData: GitInMemoryRebaseData,
  showFailureNotification: Boolean,
  rebaseActivity: StructuredIdeActivity,
): GitCommitEditingOperationResult {
  return try {
    GitInMemoryInteractiveRebaseProcess(objectRepo, rebaseData).execute(showFailureNotification)
  }
  catch (e: MergeConflictException) {
    if (showFailureNotification) {
      notifyMergeConflict(objectRepo.repository, e)
    }
    GitCommitEditingOperationResult.Conflict(e.description)
  }
  catch (e: CancellationException) {
    GitOperationsCollector.endInMemoryInteractiveRebase(rebaseActivity, InMemoryRebaseResult.CANCELED)
    throw e
  }
  catch (e: Exception) {
    GitOperationsCollector.endInMemoryInteractiveRebase(rebaseActivity, InMemoryRebaseResult.ERROR)
    throw e
  }
}

private fun notifyValidationFailure(repository: GitRepository, @Nls reason: String) {
  val message = GitBundle.message("in.memory.rebase.failed.rebase.data") + "\n" + reason +
                "\n\n" + GitBundle.message("in.memory.rebase.interactive.failed.message")
  VcsNotifier.getInstance(repository.project).notifyError(
    GitNotificationIdsHolder.IN_MEMORY_REBASE_VALIDATION_FAILED,
    GitBundle.message("in.memory.rebase.interactive.failed.title"),
    message,
  )
}

private fun notifyMergeConflict(repository: GitRepository, exception: MergeConflictException) {
  val message = exception.message + "\n\n" + GitBundle.message("in.memory.rebase.interactive.failed.message")
  VcsNotifier.getInstance(repository.project).notifyError(
    GitNotificationIdsHolder.IN_MEMORY_REBASE_MERGE_CONFLICT,
    GitBundle.message("in.memory.rebase.interactive.failed.title"),
    message,
  )
}

/**
 * Represents the data needed to perform an in-memory rebase.
 * [baseCommit] is needed to load initial range of commits (entries may be reordered)
 * [initialHead] is used to validate that HEAD didn't move during rebase
 */
internal data class GitInMemoryRebaseData(
  val entries: List<GitRebaseEntry>,
  val baseCommit: Hash,
  val initialHead: Hash,
) {
  companion object {
    fun createValidatedRebaseData(model: GitRebaseTodoModel<out GitRebaseEntry>, baseCommit: Hash, initialHead: Hash): ValidationResult {
      val entries = model.elements.map { element ->
        if (element.type is GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword) {
          GitRebaseRewordEntryWithMessage(GitRebaseEntry(element.type.command, element.entry.commit, element.entry.subject),
                                          (element.type as GitRebaseTodoModel.Type.NonUnite.KeepCommit.Reword).newMessage)
        }
        else {
          GitRebaseEntry(element.type.command, element.entry.commit, element.entry.subject)
        }
      }

      val rebaseData = GitInMemoryRebaseData(entries, baseCommit, initialHead)
      return validate(rebaseData)
    }

    private fun validate(rebaseData: GitInMemoryRebaseData): ValidationResult {
      val unsupportedEntry = rebaseData.entries.find { it.action::class !in GitInMemoryInteractiveRebaseProcess.SUPPORTED_ACTIONS }

      if (unsupportedEntry != null) {
        return ValidationResult.Invalid(
          GitBundle.message("in.memory.rebase.data.contains.entry.with.unsupported.action", unsupportedEntry)
        )
      }

      val withoutMessageEntry = rebaseData.entries.find { it.action is GitRebaseEntry.Action.REWORD && it !is GitRebaseRewordEntryWithMessage }
      if (withoutMessageEntry != null) {
        return ValidationResult.Invalid(
          GitBundle.message("in.memory.rebase.data.contains.missing.reword.message.for.entry", withoutMessageEntry)
        )
      }

      val blankMessageEntry = rebaseData.entries.find { it.action is GitRebaseEntry.Action.REWORD && it is GitRebaseRewordEntryWithMessage && it.newMessage.isBlank() }
      if (blankMessageEntry != null) {
        return ValidationResult.Invalid(
          GitBundle.message("in.memory.rebase.data.contains.empty.reword.message.for.entry", blankMessageEntry)
        )
      }

      return ValidationResult.Valid(rebaseData)
    }

    sealed class ValidationResult {
      data class Valid(val rebaseData: GitInMemoryRebaseData) : ValidationResult()
      data class Invalid(val reason: @Nls String) : ValidationResult()
    }
  }
}

internal class GitRebaseRewordEntryWithMessage(
  entry: GitRebaseEntry,
  val newMessage: String,
) : GitRebaseEntry(entry.action, entry.commit, entry.subject)

internal enum class InMemoryRebaseResult {
  SUCCESS,
  CONFLICT,
  CANCELED,
  ERROR
}