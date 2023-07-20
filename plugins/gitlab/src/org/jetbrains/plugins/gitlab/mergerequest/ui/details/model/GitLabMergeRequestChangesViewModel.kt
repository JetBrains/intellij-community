// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.diff.isEqual

internal interface GitLabMergeRequestChangesViewModel : CodeReviewChangesViewModel<GitLabCommitDTO> {
  val changeListVm: SharedFlow<Result<GitLabMergeRequestChangeListViewModel>>

  fun selectChange(change: Change)
}

private val LOG = logger<GitLabMergeRequestChangesViewModel>()

internal class GitLabMergeRequestChangesViewModelImpl(
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestChangesViewModel,
    CodeReviewChangesViewModel<GitLabCommitDTO> {
  private val cs = parentCs.childScope()

  override val reviewCommits: Flow<List<GitLabCommitDTO>> =
    mergeRequest.changes.map { it.commits }.modelFlow(cs, LOG)

  override val selectedCommitIndex = MutableStateFlow(-1)

  override val selectedCommit: Flow<GitLabCommitDTO?> = reviewCommits.combine(selectedCommitIndex) { commits, index ->
    index.takeIf { it >= 0 }?.let { commits[it] }
  }

  private val selectionRequests = MutableSharedFlow<ChangesRequest>()

  override fun selectCommit(index: Int) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.Commit(index))
    }
  }

  override fun selectNextCommit() {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.NextCommit)
    }
  }

  override fun selectPreviousCommit() {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.PrevCommit)
    }
  }

  override fun selectChange(change: Change) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.SelectChange(change))
    }
  }


  override val changeListVm: SharedFlow<Result<GitLabMergeRequestChangeListViewModelImpl>> =
    mergeRequest.changes.manageChangeListVm().modelFlow(cs, LOG)

  private fun Flow<GitLabMergeRequestChanges>.manageChangeListVm(): Flow<Result<GitLabMergeRequestChangeListViewModelImpl>> =
    channelFlow {
      val vm: GitLabMergeRequestChangeListViewModelImpl = createChangesVm()
      collectLatest { allChanges ->
        val parsedChanges: GitBranchComparisonResult = try {
          allChanges.getParsedChanges()
        }
        catch (ce: CancellationException) {
          throw ce
        }
        catch (e: Exception) {
          send(Result.failure(e))
          return@collectLatest
        }
        val commits: List<GitLabCommitDTO> = allChanges.commits

        vm.updatesChanges(parsedChanges, selectedCommitIndex.value)
        send(Result.success(vm))

        fun updateCommit(change: Change?, indexUpdater: (current: Int) -> Int) {
          val newIndex = selectedCommitIndex.updateAndGet { current ->
            indexUpdater(current).let {
              if (it !in -1..commits.lastIndex) {
                -1
              }
              else {
                it
              }
            }
          }
          vm.updatesChanges(parsedChanges, newIndex, change)
        }

        selectionRequests.collect { request ->
          when (request) {
            is ChangesRequest.Commit -> {
              updateCommit(null) { request.index }
            }
            ChangesRequest.NextCommit -> {
              updateCommit(null) { it + 1 }
            }
            ChangesRequest.PrevCommit -> {
              updateCommit(null) { it - 1 }
            }
            is ChangesRequest.SelectChange -> {
              val commitIndex = findCommitIndexForChange(commits, parsedChanges, request.change) ?: return@collect
              updateCommit(request.change) { commitIndex }
            }
          }
        }
      }
    }

  private fun findCommitIndexForChange(commits: List<GitLabCommitDTO>,
                                       parsedChanges: GitBranchComparisonResult,
                                       change: Change): Int? =
    if (parsedChanges.changes.find { it.isEqual(change) } != null) {
      -1
    }
    else {
      parsedChanges.commitByChange[change]?.let { commitSha -> commits.indexOfFirst { it.sha == commitSha } }
    }

  private fun CoroutineScope.createChangesVm(): GitLabMergeRequestChangeListViewModelImpl =
    GitLabMergeRequestChangeListViewModelImpl(this, mergeRequest)

  override fun commitHash(commit: GitLabCommitDTO): String = commit.shortId
}

private sealed interface ChangesRequest {
  data class Commit(val index: Int) : ChangesRequest
  object NextCommit : ChangesRequest
  object PrevCommit : ChangesRequest
  data class SelectChange(val change: Change) : ChangesRequest
}
