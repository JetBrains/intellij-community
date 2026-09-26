// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.asDisposable
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitOperationsCollector.logCantRebaseUsingLog
import git4idea.GitUtil
import git4idea.config.GitConfigUtil.isRebaseUpdateRefsEnabledCached
import git4idea.history.GitHistoryTraverser
import git4idea.history.GitHistoryTraverserImpl
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseEntryWithDetails
import git4idea.rebase.GitSquashedCommitsMessage
import git4idea.repo.GitRepository
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.VisibleForTesting

@Service(Service.Level.PROJECT)
internal class GitInteractiveRebaseEntriesProvider {
  /**
   * Generate rebase entries using log for the dialog
   * Fails when we can't build them identical to git, i.e.,
   * we have fixup! commits that should be rearranged or there should be an update-ref entry
   */
  suspend fun tryGetEntriesForDialog(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    logData: VcsLogData? = null,
  ): List<GitRebaseEntryGeneratedUsingLog>? {
    return tryGetEntries(repository, logData) { actualLogData ->
      getEntriesForDialog(repository, commit, actualLogData)
    }
  }

  /**
   * @param head the tip commit to collect entries down to [commit] from; defaults to the repository HEAD.
   * Callers that append their own newest entry (e.g. amend-to-specific-commit) pass the parent of that entry.
   */
  suspend fun tryGetEntriesForCommitEditing(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    logData: VcsLogData? = null,
    head: Hash? = null,
  ): List<GitRebaseEntryGeneratedUsingLog>? {
    return tryGetEntries(repository, logData) { actualLogData ->
      getEntriesForCommitEditing(repository, commit, actualLogData, head)
    }
  }

  private suspend fun tryGetEntries(
    repository: GitRepository,
    logData: VcsLogData?,
    entriesProvider: suspend (VcsLogData) -> GetEntriesUsingLogResult,
  ): List<GitRebaseEntryGeneratedUsingLog>? {
    return withBackgroundProgress(repository.project, GitBundle.message("rebase.progress.indicator.preparing.title")) {
      val actualLogData = logData ?: VcsProjectLog.awaitLogIsReady(repository.project)?.dataManager ?: run {
        LOG.warn("Couldn't use log for rebasing - log not available")
        return@withBackgroundProgress null
      }

      when (val result = entriesProvider(actualLogData)) {
        is GetEntriesUsingLogResult.Success -> result.entries
        is GetEntriesUsingLogResult.Failure -> {
          logCantRebaseUsingLog(repository.project, result.reason)
          LOG.warn("Couldn't use log for rebasing: ${result.reason}")
          null
        }
      }
    }
  }

  @VisibleForTesting
  suspend fun getEntriesForDialog(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    logData: VcsLogData,
  ): GetEntriesUsingLogResult {
    val result = getEntriesForCommitEditing(repository, commit, logData, head = null)

    if (result is GetEntriesUsingLogResult.Success) {
      if (result.entries.any { entry -> GitSquashedCommitsMessage.isAutosquashCommitMessage(entry.commitDetails.subject) }) {
        return GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.FIXUP_SQUASH)
      }
      if (isRebaseUpdateRefsEnabledCached(repository.project, repository.root)) {
        return GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.UPDATE_REFS)
      }
    }
    return result
  }

  private suspend fun getEntriesForCommitEditing(
    repository: GitRepository,
    commit: VcsShortCommitDetails,
    logData: VcsLogData,
    head: Hash?,
  ): GetEntriesUsingLogResult =
    coroutineScope {
      // Start the walk from a concrete tip commit (the repository HEAD by default) instead of the log's HEAD ref.
      // If that commit is not in the current data pack yet - e.g. a commit was just made and the log refresh hasn't
      // finished - the traversal fails fast (the hash is absent from the graph, caught below), so we fall back to
      // Git-native generation instead of silently walking from a stale HEAD and dropping recent commits.
      val startHash = head ?: repository.currentRevision?.let(HashImpl::build)
                      ?: return@coroutineScope GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.LOG_NOT_UP_TO_DATE)
      val startNode = GitHistoryTraverser.StartNode.CommitHash(startHash)

      val traverser: GitHistoryTraverser = GitHistoryTraverserImpl(repository.project, logData, this.asDisposable())
      val details = mutableListOf<VcsCommitMetadata>()
      try {
        traverser.traverse(repository.root, startNode) { (commitId, parents) ->
          loadMetadataLater(commitId) { metadata ->
            details.add(metadata)
          }

          val hash = traverser.toHash(commitId)
          parents.size <= 1 && hash != commit.id // stop when we reach merge commit or target commit
        }
      }
      catch (_: IllegalArgumentException) {
        // The repository HEAD is not present in the current data pack yet, i.e. the log is behind the repository.
        return@coroutineScope GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.LOG_NOT_UP_TO_DATE)
      }
      catch (_: VcsException) {
        return@coroutineScope GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.UNRESOLVED_HASH)
      }

      if (details.last().parents.size > 1) {
        return@coroutineScope GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.MERGE)
      }

      if (details.last().id != commit.id) {
        return@coroutineScope GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.UNEXPECTED_HASH)
      }

      return@coroutineScope GetEntriesUsingLogResult.Success(details.map { GitRebaseEntryGeneratedUsingLog(it) }.reversed())
    }

  companion object {
    private val LOG = thisLogger()
  }
}

internal sealed interface GetEntriesUsingLogResult {
  data class Success(val entries: List<GitRebaseEntryGeneratedUsingLog>) : GetEntriesUsingLogResult
  data class Failure(val reason: FailureReason) : GetEntriesUsingLogResult

  enum class FailureReason {
    MERGE,
    FIXUP_SQUASH,
    UNEXPECTED_HASH,
    UNRESOLVED_HASH,
    UPDATE_REFS, // should generate an update-ref entry in the editor, which is not supported when using log
    LOG_NOT_UP_TO_DATE // the VCS log is behind the repository HEAD, so generated entries would miss recent commits
  }
}

internal class GitRebaseEntryGeneratedUsingLog(details: VcsCommitMetadata) :
  GitRebaseEntryWithDetails(GitRebaseEntry(Action.PICK, details.id.asString(), details.subject.trimStart()), details) {

  fun equalsWithReal(realEntry: GitRebaseEntry) =
    if (GitUtil.isPossibleHash(realEntry.commit)) {
      action == realEntry.action && (commit.startsWith(realEntry.commit) || realEntry.commit.startsWith(commit))
    }
    else false
}
