// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeDetails
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeList
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModelBase
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import java.util.concurrent.ConcurrentHashMap

interface GitLabMergeRequestChangeListViewModel
  : CodeReviewChangeListViewModel.WithDetails,
    CodeReviewChangeListViewModel.WithGrouping {
  val isOnLatest: Boolean

  fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean)
}

internal class GitLabMergeRequestChangeListViewModelImpl(
  override val project: Project,
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  private val parsedChanges: GitBranchComparisonResult,
  changeList: CodeReviewChangeList,
) : CodeReviewChangeListViewModelBase(parentCs, changeList),
    GitLabMergeRequestChangeListViewModel {
  private val persistentChangesViewedState = project.service<GitLabPersistentMergeRequestChangesViewedState>()
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests: Flow<Unit> = _showDiffRequests.asSharedFlow()

  override val isOnLatest: Boolean = selectedCommit == null || parsedChanges.commits.size == 1

  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    combine(
      createUnresolvedDiscussionsPositionsFlow(mergeRequest),
      createUnresolvedDraftsPositionsFlow(mergeRequest),
      persistentChangesViewedState.updatesFlow.withInitial(Unit),
    ) { discPos, draftsPos, _ ->
      changes.associateWith { change ->
        val sha = parsedChanges.findLatestCommitWithChangesTo(mergeRequest.gitRepository, change.filePath)
        val isRead = !isOnLatest || sha?.let {
          persistentChangesViewedState.isViewed(
            mergeRequest.glProject, mergeRequest.iid,
            mergeRequest.gitRepository,
            change.filePath, it
          )
        } ?: false

        val patch = parsedChanges.patchesByChange[change]
        if (patch == null) {
          CodeReviewChangeDetails(isRead, 0)
        }
        else {
          //TODO: cache?
          val discussions = discPos.count { it.mapToLocation(patch) != null } + draftsPos.count { it.mapToLocation(patch) != null }
          CodeReviewChangeDetails(isRead, discussions)
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, emptyMap())

  override val grouping: StateFlow<Set<String>> = preferences.changesGroupingState

  override fun showDiffPreview() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }

  // TODO: separate diff
  override fun showDiff() = showDiffPreview()

  override fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean) {
    val filePathsWithShas = changes.mapNotNull { change ->
      val path = change.filePath
      parsedChanges.findLatestCommitWithChangesTo(mergeRequest.gitRepository, path)?.let {
        path to it
      }
    }
    persistentChangesViewedState.markViewed(
      mergeRequest.glProject, mergeRequest.iid,
      mergeRequest.gitRepository,
      filePathsWithShas,
      viewed
    )
  }

  override fun setGrouping(grouping: Collection<String>) {
    preferences.changesGrouping = grouping.toSet()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun createUnresolvedDiscussionsPositionsFlow(mergeRequest: GitLabMergeRequest) = channelFlow {
  withContext(CoroutineName("GitLab Merge Request discussions positions collector")) {
    val discussionsCache = ConcurrentHashMap<GitLabId, GitLabNotePosition>()
    mergeRequest.discussions.collectLatest { discussionsResult ->
      coroutineScope {
        discussionsCache.clear()
        val discussions = discussionsResult.getOrNull().orEmpty()
        if (discussions.isEmpty()) {
          send(emptyList())
        }
        for (disc in discussions) {
          launchNow {
            val positionFlow = disc.firstNote.filterNotNull().flatMapLatest { it.position }
            combine(disc.resolved, positionFlow) { resolved, position ->
              if (resolved) null else position
            }.collectLatest {
              if (it != null) discussionsCache[disc.id] = it else discussionsCache.remove(disc.id)
              send(discussionsCache.values.toList())
            }
          }
        }
      }
    }
  }
}

private fun createUnresolvedDraftsPositionsFlow(mergeRequest: GitLabMergeRequest) = channelFlow {
  withContext(CoroutineName("GitLab Merge Request draft notes positions collector")) {
    val draftNotesCache = ConcurrentHashMap<GitLabId, GitLabNotePosition>()
    mergeRequest.draftNotes.collectLatest { notesResult ->
      coroutineScope {
        draftNotesCache.clear()
        val notes = notesResult.getOrNull().orEmpty()
        if (notes.isEmpty()) {
          send(emptyList())
        }
        for (note in notes) {
          launchNow {
            note.position.collectLatest {
              if (it != null) draftNotesCache[note.id] = it else draftNotesCache.remove(note.id)
              send(draftNotesCache.values.toList())
            }
          }
        }
      }
    }
  }
}
