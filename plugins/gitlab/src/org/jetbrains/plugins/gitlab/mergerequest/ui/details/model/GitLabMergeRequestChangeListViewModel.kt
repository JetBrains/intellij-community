// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeDetails
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeList
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModelBase
import com.intellij.collaboration.util.CODE_REVIEW_CHANGE_HASHING_STRATEGY
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.firstNote
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import java.util.concurrent.ConcurrentHashMap

internal interface GitLabMergeRequestChangeListViewModel : CodeReviewChangeListViewModel.WithDetails {
  /**
   * Request standalone diff for [changesSelection]
   */
  fun showDiff()

  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestChangeListViewModel>("GitLab.MergeRequest.Changes.ViewModel")
  }
}

internal class GitLabMergeRequestChangeListViewModelImpl(
  override val project: Project,
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest,
  changeList: CodeReviewChangeList
) : CodeReviewChangeListViewModelBase(parentCs, changeList),
    GitLabMergeRequestChangeListViewModel {

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests: Flow<Unit> = _showDiffRequests.asSharedFlow()

  override val detailsByChange: StateFlow<Map<Change, CodeReviewChangeDetails>> =
    combine(createUnresolvedDiscussionsPositionsFlow(mergeRequest),
            createUnresolvedDraftsPositionsFlow(mergeRequest),
            mergeRequest.changes.map { it.getParsedChanges() }.catch { }) { discPos, draftsPos, parsedChanges ->
      val result: MutableMap<Change, CodeReviewChangeDetails> =
        CollectionFactory.createCustomHashingStrategyMap(CODE_REVIEW_CHANGE_HASHING_STRATEGY)
      changeList.changes.associateWithTo(result) { change ->
        val patch = parsedChanges.patchesByChange[change] ?: return@associateWithTo CodeReviewChangeDetails(true, 0)
        //TODO: cache?
        val discussions = discPos.count { it.mapToLocation(patch) != null } + draftsPos.count { it.mapToLocation(patch) != null }
        CodeReviewChangeDetails(true, discussions)
      }
    }.stateIn(cs, SharingStarted.Eagerly, emptyMap())

  override fun showDiffPreview() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }

  // TODO: separate diff
  override fun showDiff() = showDiffPreview()
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
