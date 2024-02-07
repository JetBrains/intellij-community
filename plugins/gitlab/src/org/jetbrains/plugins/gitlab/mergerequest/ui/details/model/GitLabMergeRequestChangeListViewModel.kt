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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.firstNote
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import java.util.concurrent.ConcurrentHashMap

interface GitLabMergeRequestChangeListViewModel : CodeReviewChangeListViewModel.WithDetails {
  val isOnLatest: StateFlow<Boolean>

  fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean)
}

internal class GitLabMergeRequestChangeListViewModelImpl(
  override val project: Project,
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  changeList: CodeReviewChangeList,
) : CodeReviewChangeListViewModelBase(parentCs, changeList),
    GitLabMergeRequestChangeListViewModel {

  private val persistentChangesViewedState = project.service<GitLabPersistentMergeRequestChangesViewedState>()

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests: Flow<Unit> = _showDiffRequests.asSharedFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  override val isOnLatest: StateFlow<Boolean> = mergeRequest.changes.mapLatest {
    selectedCommit == null || it.commits.await().size == 1
  }.stateIn(cs, SharingStarted.Lazily, selectedCommit == null)

  @OptIn(ExperimentalCoroutinesApi::class)
  override val detailsByChange: StateFlow<Map<RefComparisonChange, CodeReviewChangeDetails>> =
    combine(createUnresolvedDiscussionsPositionsFlow(mergeRequest),
            createUnresolvedDraftsPositionsFlow(mergeRequest),
            mergeRequest.changes.map { it.getParsedChanges() }.catch { }) { discPos, draftsPos, parsedChanges ->
      persistentChangesViewedState.updatesFlow.withInitial(Unit).combine(isOnLatest) { _, isOnLatest ->
        changes.associateWith { change ->
          val isRead = !isOnLatest || persistentChangesViewedState.isViewed(mergeRequest, change.filePath, change.revisionNumberAfter.toString())
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
      }
    }.flatMapLatest { it }.stateIn(cs, SharingStarted.Eagerly, emptyMap())

  override fun showDiffPreview() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }

  // TODO: separate diff
  override fun showDiff() = showDiffPreview()

  override fun setViewedState(changes: Iterable<RefComparisonChange>, viewed: Boolean) {
    persistentChangesViewedState.markViewed(mergeRequest, changes.map { it.filePath },
                                            changes.first().revisionNumberAfter.toString(), viewed)
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
