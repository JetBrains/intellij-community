// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.asDisposable
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitOperationsCollector.logCantRebaseUsingLog
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
  suspend fun tryGetEntriesUsingLog(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    logData: VcsLogData? = null,
  ): List<GitRebaseEntryGeneratedUsingLog>? {
    return withBackgroundProgress(repository.project, GitBundle.message("rebase.progress.indicator.preparing.title")) {
      val logData = logData ?: VcsProjectLog.awaitLogIsReady(repository.project)?.dataManager ?: run {
        LOG.warn("Couldn't use log for rebasing - log not available")
        return@withBackgroundProgress null
      }

      val result = getEntriesUsingLog(repository, commit, logData)

      when (result) {
        is GetEntriesUsingLogResult.Success -> result.entries
        is GetEntriesUsingLogResult.Failure -> {
          LOG.warn("Couldn't use log for rebasing: ${result.reason}")
          logCantRebaseUsingLog(repository.project, result.reason)
          null
        }
      }
    }
  }

  @VisibleForTesting
  suspend fun getEntriesUsingLog(
    repository: GitRepository,
    commit: VcsShortCommitDetails,
    logData: VcsLogData,
  ): GetEntriesUsingLogResult =
    coroutineScope {
      val traverser: GitHistoryTraverser = GitHistoryTraverserImpl(repository.project, logData, this.asDisposable())
      val details = mutableListOf<VcsCommitMetadata>()
      try {
        traverser.traverse(repository.root) { (commitId, parents) ->
          loadMetadataLater(commitId) { metadata ->
            details.add(metadata)
          }

          val hash = traverser.toHash(commitId)
          parents.size <= 1 && hash != commit.id // stop when we reach merge commit or target commit
        }
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

      if (details.any { detail -> GitSquashedCommitsMessage.isAutosquashCommitMessage(detail.subject) }) {
        return@coroutineScope GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.FIXUP_SQUASH)
      }

      if (isRebaseUpdateRefsEnabledCached(repository.project, repository.root)) {
        return@coroutineScope GetEntriesUsingLogResult.Failure(GetEntriesUsingLogResult.FailureReason.UPDATE_REFS)
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
    UPDATE_REFS // should generate an update-ref entry in the editor, which is not supported when using log
  }
}

internal class GitRebaseEntryGeneratedUsingLog(details: VcsCommitMetadata) :
  GitRebaseEntryWithDetails(GitRebaseEntry(Action.PICK, details.id.asString(), details.subject.trimStart()), details) {

  fun equalsWithReal(realEntry: GitRebaseEntry) =
    if (VcsLogUtil.HASH_PREFIX_REGEX.matcher(realEntry.commit).matches()) {
      action == realEntry.action && (commit.startsWith(realEntry.commit) || realEntry.commit.startsWith(commit))
    }
    else false
}
