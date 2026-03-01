// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import git4idea.DialogManager
import git4idea.GitOperationsCollector
import git4idea.GitOperationsCollector.logRebaseStartUsingLog
import git4idea.branch.GitRebaseParams
import git4idea.i18n.GitBundle
import git4idea.inMemory.GitObjectRepository
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.toHash
import git4idea.inMemory.rebase.performInMemoryRebase
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEditorHandler
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseUtils
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitInteractiveRebaseEntriesProvider
import git4idea.rebase.log.GitRebaseEntryGeneratedUsingLog
import git4idea.repo.GitRepository
import com.intellij.openapi.vcs.VcsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = Logger.getInstance("Git.Interactive.Rebase.Using.Log")

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
internal suspend fun interactivelyRebaseUsingLog(repository: GitRepository, commit: VcsCommitMetadata, logData: VcsLogData) {
  val project = repository.project
  val root = repository.root

  val generatedEntries = repository.project.service<GitInteractiveRebaseEntriesProvider>()
    .tryGetEntriesUsingLog(repository, commit, logData)
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
        val objectRepo = GitObjectRepository(repository)
        val inMemoryResult = performInMemoryRebase(objectRepo, generatedEntries, model)
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
  return getRebaseUpstreamFor(commit.id, commit.parents)
}

internal fun getRebaseUpstreamFor(commit: GitObject.Commit): GitRebaseParams.RebaseUpstream {
  return getRebaseUpstreamFor(commit.oid.toHash(), commit.parentsOids.map { it.toHash() })
}

internal fun getRebaseUpstreamFor(commit: Hash, parents: List<Hash>): GitRebaseParams.RebaseUpstream {
  when {
    parents.isEmpty() -> return GitRebaseParams.RebaseUpstream.Root
    parents.size == 1 -> return GitRebaseParams.RebaseUpstream.Commit(parents.single())
    else -> {
      LOG.warn(Throwable("Unexpected rebase of a merge commit: $commit, parents: ${parents.joinToString(" ")}"))
      return GitRebaseParams.RebaseUpstream.Commit(parents.first())
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
