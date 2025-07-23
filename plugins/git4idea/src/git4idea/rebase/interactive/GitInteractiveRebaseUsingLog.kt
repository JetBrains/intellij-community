// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.DialogManager
import git4idea.GitOperationsCollector
import git4idea.branch.GitRebaseParams
import git4idea.history.GitHistoryTraverser
import git4idea.history.GitHistoryTraverserImpl
import git4idea.i18n.GitBundle
import git4idea.rebase.*
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.repo.GitRepository

private val LOG = Logger.getInstance("Git.Interactive.Rebase.Using.Log")

@VisibleForTesting
@Throws(CantRebaseUsingLogException::class)
internal fun getEntriesUsingLog(
  repository: GitRepository,
  commit: VcsShortCommitDetails,
  logData: VcsLogData,
): List<GitRebaseEntryGeneratedUsingLog> {
  Disposer.newDisposable().use {
    val traverser: GitHistoryTraverser = GitHistoryTraverserImpl(repository.project, logData, it)
    val details = mutableListOf<VcsCommitMetadata>()
    try {
      traverser.traverse(repository.root) { (commitId, parents) ->
        // commit is not root or merge
        if (parents.size == 1) {
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
    catch (e: VcsException) {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UNRESOLVED_HASH)
    }

    if (details.last().id != commit.id) {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UNEXPECTED_HASH)
    }

    if (details.any { detail -> GitSquashedCommitsMessage.isAutosquashCommitMessage(detail.subject) }) {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.FIXUP_SQUASH)
    }

    return details.map { GitRebaseEntryGeneratedUsingLog(it) }.reversed()
  }
}

internal fun interactivelyRebaseUsingLog(repository: GitRepository, commit: VcsShortCommitDetails, logData: VcsLogData) {
  val project = repository.project
  val root = repository.root

  object : Task.Backgroundable(project, GitBundle.message("rebase.progress.indicator.preparing.title")) {
    private var generatedEntries: List<GitRebaseEntryGeneratedUsingLog>? = null

    override fun run(indicator: ProgressIndicator) {
      try {
        generatedEntries = getEntriesUsingLog(repository, commit, logData)
      }
      catch (e: CantRebaseUsingLogException) {
        LOG.warn("Couldn't use log for rebasing: ${e.message}")
      }
    }

    override fun onSuccess() {
      generatedEntries?.let { entries ->
        val dialog = GitInteractiveRebaseDialog(project, root, entries)
        DialogManager.show(dialog)
        if (dialog.isOK) {
          startInteractiveRebase(repository, commit, GitInteractiveRebaseUsingLogEditorHandler(repository, entries, dialog.getModel()))
        }
      } ?: startInteractiveRebase(repository, commit)
    }
  }.queue()
}

internal fun startInteractiveRebase(
  repository: GitRepository,
  commit: VcsShortCommitDetails,
  editorHandler: GitRebaseEditorHandler? = null,
) {
  object : Task.Backgroundable(repository.project, GitBundle.message("rebase.progress.indicator.title")) {
    override fun run(indicator: ProgressIndicator) {
      val base = getRebaseUpstreamFor(commit)
      val params = GitRebaseParams.editCommits(repository.vcs.version, base, editorHandler, false)
      GitRebaseUtils.rebase(repository.project, listOf(repository), params, indicator)
    }
  }.queue()
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

@VisibleForTesting
internal class CantRebaseUsingLogException(val reason: Reason) : Exception(reason.toString()) {
  enum class Reason {
    MERGE,
    FIXUP_SQUASH,
    UNEXPECTED_HASH,
    UNRESOLVED_HASH
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