// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.DialogManager
import git4idea.GitOperationsCollector
import git4idea.GitOperationsCollector.logCantRebaseUsingLog
import git4idea.GitOperationsCollector.logRebaseStartUsingLog
import git4idea.branch.GitRebaseParams
import git4idea.config.GitConfigUtil.isRebaseUpdateRefsEnabledCached
import git4idea.history.GitHistoryTraverser
import git4idea.history.GitHistoryTraverserImpl
import git4idea.i18n.GitBundle
import git4idea.inMemory.rebase.performInMemoryRebase
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEditorHandler
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.GitRebaseUtils
import git4idea.rebase.GitSquashedCommitsMessage
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = Logger.getInstance("Git.Interactive.Rebase.Using.Log")

@VisibleForTesting
@Throws(CantRebaseUsingLogException::class)
internal fun getEntriesUsingLog(
  repository: GitRepository,
  commit: VcsShortCommitDetails,
  logData: VcsLogData,
): List<GitRebaseEntryGeneratedUsingLog> {
  Disposer.newDisposable().use { parentDisposable ->
    val traverser: GitHistoryTraverser = GitHistoryTraverserImpl(repository.project, logData, parentDisposable)
    val details = mutableListOf<VcsCommitMetadata>()
    try {
      traverser.traverse(repository.root) { (commitId, parents) ->
        // commit is not merge
        if (parents.size <= 1) {
          loadMetadataLater(commitId) { metadata ->
            details.add(metadata)
          }
          val hash = traverser.toHash(commitId)
          hash != commit.id
        }
        else {
          throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.MERGE)
        }
      }
    }
    catch (_: VcsException) {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UNRESOLVED_HASH)
    }

    if (details.last().id != commit.id) {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UNEXPECTED_HASH)
    }

    if (details.any { detail -> GitSquashedCommitsMessage.isAutosquashCommitMessage(detail.subject) }) {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.FIXUP_SQUASH)
    }

    if (isRebaseUpdateRefsEnabledCached(repository.project, repository.root)) {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UPDATE_REFS)
    }

    return details.map { GitRebaseEntryGeneratedUsingLog(it) }.reversed()
  }
}

internal suspend fun tryGetEntriesUsingLog(
  repository: GitRepository,
  commit: VcsCommitMetadata,
  logData: VcsLogData? = null,
): List<GitRebaseEntryGeneratedUsingLog>? {
  val generatedEntries = try {
    withBackgroundProgress(repository.project, GitBundle.message("rebase.progress.indicator.preparing.title")) {
      val logData = logData ?: VcsProjectLog.awaitLogIsReady(repository.project)?.dataManager ?: run {
        LOG.warn("Couldn't use log for rebasing - log not available")
        return@withBackgroundProgress null
      }
      getEntriesUsingLog(repository, commit, logData)
    }
  }
  catch (e: CantRebaseUsingLogException) {
    LOG.warn("Couldn't use log for rebasing: ${e.message}")
    logCantRebaseUsingLog(repository.project, e.reason)
    null
  }
  return generatedEntries
}

/**
 * The process:
 * 1. Generate rebase entries from VCS log data
 * 2. Show a dialog for user to modify the rebase plan
 * 3. Attempt in-memory rebase if we don't have EDIT entries (faster, no working directory and index changes)
 * 4. Fall back to traditional Git rebase if in-memory rebase had to stop (merge conflict).
 *    We lose all our in-memory progress
 *
 * If log-based entry generation fails, falls back to traditional Git interactive rebase that gets entries from the editor.
 */
internal suspend fun interactivelyRebaseUsingLog(repository: GitRepository, commit: VcsCommitMetadata, logData: VcsLogData? = null) {
  val project = repository.project
  val root = repository.root

  val generatedEntries = tryGetEntriesUsingLog(repository, commit, logData)
  if (generatedEntries == null) {
    startInteractiveRebase(repository, commit)
    return
  }

  val dialog = withContext(Dispatchers.EDT) {
    GitInteractiveRebaseDialog(project, root, generatedEntries).also {
      DialogManager.show(it)
    }
  }
  if (dialog.isOK) {
    val model = dialog.getModel()

    val hasEditActions = model.elements.any { entry ->
      entry.type.command == GitRebaseEntry.Action.EDIT
    }
    logRebaseStartUsingLog(repository.project, model.elements.map { it.type.command })
    val shouldTryInMemory = Registry.`is`("git.in.memory.commit.editing.operations.enabled")

    withBackgroundProgress(repository.project, GitBundle.message("rebase.progress.indicator.title")) {
      if (!hasEditActions && shouldTryInMemory) {
        val inMemoryResult = performInMemoryRebase(repository, generatedEntries, model)
        if (inMemoryResult is GitCommitEditingOperationResult.Complete) return@withBackgroundProgress
      }

      performInteractiveRebase(repository, commit, GitInteractiveRebaseUsingLogEditorHandler(repository, generatedEntries, model))
    }
  }
}

/**
 * Starts a traditional Git interactive rebase process.
 */
internal suspend fun startInteractiveRebase(
  repository: GitRepository,
  commit: VcsShortCommitDetails,
  editorHandler: GitRebaseEditorHandler? = null,
) {
  withBackgroundProgress(repository.project, GitBundle.message("rebase.progress.indicator.title")) {
    performInteractiveRebase(repository, commit, editorHandler)
  }
}

private suspend fun performInteractiveRebase(
  repository: GitRepository,
  commit: VcsShortCommitDetails,
  editorHandler: GitRebaseEditorHandler? = null,
) {
  coroutineToIndicator { indicator ->
    val base = getRebaseUpstreamFor(commit)
    val params = GitRebaseParams.editCommits(repository.vcs.version, base, editorHandler, false)

    val rebaseActivity = GitOperationsCollector.startInteractiveRebase(repository.project)
    try {
      val wasSuccessful = GitRebaseUtils.rebaseWithResult(repository.project, listOf(repository), params, indicator)
      GitOperationsCollector.endInteractiveRebase(rebaseActivity, wasSuccessful)
    }
    catch (e: Exception) {
      GitOperationsCollector.endInteractiveRebase(rebaseActivity, false)
      throw e
    }
  }
}

internal fun getRebaseUpstreamFor(commit: VcsShortCommitDetails): GitRebaseParams.RebaseUpstream {
  when {
    commit.parents.isEmpty() -> return GitRebaseParams.RebaseUpstream.Root
    commit.parents.size == 1 -> return GitRebaseParams.RebaseUpstream.Commit(commit.parents.single())
    else -> {
      LOG.warn(Throwable("Unexpected rebase of a merge commit: $commit, parents: ${commit.parents.joinToString(" ")}"))
      return GitRebaseParams.RebaseUpstream.Commit(commit.parents.first())
    }
  }
}

private class GitInteractiveRebaseUsingLogEditorHandler(
  private val repository: GitRepository,
  private val entriesGeneratedUsingLog: List<GitRebaseEntryGeneratedUsingLog>,
  private val rebaseTodoModel: GitRebaseTodoModel<GitRebaseEntryGeneratedUsingLog>,
) : GitInteractiveRebaseEditorHandler(repository.project, repository.root) {
  private var rebaseFailed = false

  override fun collectNewEntries(entries: List<GitRebaseEntry>): List<GitRebaseEntry>? {
    if (rebaseFailed) {
      return super.collectNewEntries(entries)
    }
    if (validateEntries(entries)) {
      processModel(rebaseTodoModel)
      return rebaseTodoModel.convertToEntries()
    }
    else {
      myRebaseEditorShown = false
      rebaseFailed = true
      GitOperationsCollector.rebaseViaLogInvalidEntries(repository.project,
                                                        expectedCommitsNumber = entries.size,
                                                        actualCommitsNumber = entriesGeneratedUsingLog.size)
      LOG.warn("Incorrect git-rebase-todo file was generated.\n" +
               "Actual - ${entriesGeneratedUsingLog.toLog()}\n" +
               "Expected - ${entries.toLog()}")
      throw VcsException(GitBundle.message("rebase.using.log.couldnt.start.error"))
    }
  }

  private fun validateEntries(entries: List<GitRebaseEntry>): Boolean {
    if (entriesGeneratedUsingLog.size != entries.size) return false

    entriesGeneratedUsingLog.forEachIndexed { i, generatedEntry ->
      val realEntry = entries[i]
      if (!generatedEntry.equalsWithReal(realEntry)) {
        return false
      }
    }
    return true
  }

  private fun List<GitRebaseEntry>.toLog(): String =
    joinToString(", ", prefix = "[", postfix = "]") { "${it.commit} (${it.action.command})" }
}

internal class CantRebaseUsingLogException(val reason: Reason) : Exception(reason.toString()) {
  enum class Reason {
    MERGE,
    FIXUP_SQUASH,
    UNEXPECTED_HASH,
    UNRESOLVED_HASH,
    UPDATE_REFS // should generate an update-ref entry in the editor, which is not supported when using log
  }
}

@VisibleForTesting
internal class GitRebaseEntryGeneratedUsingLog(details: VcsCommitMetadata) :
  GitRebaseEntryWithDetails(GitRebaseEntry(Action.PICK, details.id.asString(), details.subject.trimStart()), details) {

  fun equalsWithReal(realEntry: GitRebaseEntry) =
    if (VcsLogUtil.HASH_PREFIX_REGEX.matcher(realEntry.commit).matches()) {
      action == realEntry.action && (commit.startsWith(realEntry.commit) || realEntry.commit.startsWith(commit))
    }
    else false
}