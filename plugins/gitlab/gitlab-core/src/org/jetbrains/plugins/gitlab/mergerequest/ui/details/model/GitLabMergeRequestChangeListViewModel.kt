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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.findLatestCommitWithChangesTo
import org.jetbrains.plugins.gitlab.mergerequest.data.firstNote
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import java.util.concurrent.ConcurrentHashMap

interface GitLabMergeRequestChangeListViewModel
  : CodeReviewChangeListViewModel.WithDetails,
    CodeReviewChangeListViewModel.WithGrouping

/**
 * A change list of the cumulative merge request diff.
 *
 * The viewed state is persisted per file path and the sha of the latest commit touching that path, so it only ever
 * describes the cumulative diff. A commit-scoped change list shows a different revision of the same file, so it does not
 * expose the viewed state at all - only this view model implements [CodeReviewChangeListViewModel.WithViewedState].
 */
interface GitLabMergeRequestCumulativeChangeListViewModel
  : GitLabMergeRequestChangeListViewModel,
    CodeReviewChangeListViewModel.WithViewedState

internal abstract class GitLabMergeRequestChangeListViewModelBase(
  override val project: Project,
  parentCs: CoroutineScope,
  protected val mergeRequest: GitLabMergeRequest,
  protected val parsedChanges: GitBranchComparisonResult,
  changeList: CodeReviewChangeList,
) : CodeReviewChangeListViewModelBase(parentCs, changeList),
    GitLabMergeRequestChangeListViewModel {
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests: Flow<Unit> = _showDiffRequests.asSharedFlow()

  protected val discussionsCountByChange: Flow<Map<RefComparisonChange, Int>> =
    combine(
      createUnresolvedDiscussionsPositionsFlow(mergeRequest),
      createUnresolvedDraftsPositionsFlow(mergeRequest),
    ) { discPos, draftsPos ->
      changes.associateWith { change ->
        val patch = parsedChanges.patchesByChange[change] ?: return@associateWith 0
        //TODO: cache?
        discPos.count { it.mapToLocation(patch) != null } + draftsPos.count { it.mapToLocation(patch) != null }
      }
    }

  override val grouping: StateFlow<Set<String>> = preferences.changesGroupingState

  override fun showDiffPreview() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }

  // TODO: separate diff
  override fun showDiff() = showDiffPreview()

  override fun setGrouping(grouping: Collection<String>) {
    preferences.changesGrouping = grouping.toSet()
  }
}

internal class GitLabMergeRequestCommitChangeListViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest,
  parsedChanges: GitBranchComparisonResult,
  changeList: CodeReviewChangeList,
) : GitLabMergeRequestChangeListViewModelBase(project, parentCs, mergeRequest, parsedChanges, changeList) {
  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    discussionsCountByChange.map { discussionsCount ->
      discussionsCount.mapValues { (_, discussions) -> CodeReviewChangeDetails(isRead = true, discussions = discussions) }
    }.stateIn(cs, SharingStarted.Eagerly, emptyMap())
}

internal class GitLabMergeRequestCumulativeChangeListViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest,
  parsedChanges: GitBranchComparisonResult,
  changeList: CodeReviewChangeList,
) : GitLabMergeRequestChangeListViewModelBase(project, parentCs, mergeRequest, parsedChanges, changeList),
    GitLabMergeRequestCumulativeChangeListViewModel {
  private val persistentChangesViewedState = project.service<GitLabPersistentMergeRequestChangesViewedState>()

  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    combine(
      discussionsCountByChange,
      persistentChangesViewedState.updatesFlow.withInitial(Unit),
    ) { discussionsCount, _ ->
      changes.associateWith { change ->
        CodeReviewChangeDetails(isViewed(change), discussionsCount[change] ?: 0)
      }
    }.stateIn(cs, SharingStarted.Eagerly, emptyMap())

  override fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean) {
    val filePathsWithShas = changes.mapNotNull { change ->
      val path = change.filePath
      parsedChanges.findLatestCommitWithChangesTo(mergeRequest.gitRemote.repository, path)?.let {
        path to it
      }
    }
    persistentChangesViewedState.markViewed(
      mergeRequest.serverPath, mergeRequest.projectId, mergeRequest.iid,
      mergeRequest.gitRemote.repository,
      filePathsWithShas,
      viewed
    )
  }

  private fun isViewed(change: RefComparisonChange): Boolean {
    val sha = parsedChanges.findLatestCommitWithChangesTo(mergeRequest.gitRemote.repository, change.filePath) ?: return false
    return persistentChangesViewedState.isViewed(
      mergeRequest.serverPath, mergeRequest.projectId, mergeRequest.iid,
      mergeRequest.gitRemote.repository,
      change.filePath, sha
    )
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
