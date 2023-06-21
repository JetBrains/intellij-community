// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import com.intellij.util.containers.CollectionFactory
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.firstNote
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.diff.ChangesSelection
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel.Update
import java.util.concurrent.ConcurrentHashMap

internal interface GitLabMergeRequestChangeListViewModel {
  /**
   * Flow of updates to changelist state
   */
  val updates: SharedFlow<Update>

  /**
   * Uni-directional state of changelist selection (changelist presentation should not collect it)
   */
  val changesSelection: StateFlow<ChangesSelection?>

  /**
   * Discussions data per changelist change
   */
  val mappedDiscussionsCounts: SharedFlow<Map<Change, Int>>

  /**
   * Publish changelist selection to [changesSelection]
   */
  fun updateSelectedChanges(selection: ChangesSelection?)

  /**
   * Request diff for [changesSelection]
   */
  fun showDiff()

  sealed class Update(val changes: List<Change>) {
    class WithSelectAll(changes: List<Change>) : Update(changes)
    class WithSelectChange(changes: List<Change>, val change: Change) : Update(changes)
  }

  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestChangeListViewModel>("GitLab.MergeRequest.Changes.ViewModel")
  }
}

internal class GitLabMergeRequestChangeListViewModelImpl(
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestChangeListViewModel {
  private val cs = parentCs.childScope()

  private val _updates = MutableSharedFlow<Update>(replay = 1)
  override val updates: SharedFlow<Update> = _updates.asSharedFlow()

  private val _changesSelection = MutableStateFlow<ChangesSelection?>(null)
  override val changesSelection: StateFlow<ChangesSelection?> = _changesSelection.asStateFlow()

  private val _changesWithCommit = MutableSharedFlow<Pair<GitBranchComparisonResult, String?>>(replay = 1)
  private val changesWithCommit = _changesWithCommit.distinctUntilChanged { old, new ->
    old.first === new.first && old.second == new.second
  }

  private val stateGuard = Mutex()

  override val mappedDiscussionsCounts: SharedFlow<Map<Change, Int>> =
    createUnresolvedDiscussionsPositionsFlow(mergeRequest).combine(changesWithCommit) { positions, pair ->
      val (parsedChanges, commit) = pair
      val changes = parsedChanges.getChanges(commit)
      val result: MutableMap<Change, Int> =
        CollectionFactory.createCustomHashingStrategyMap(GitBranchComparisonResult.REVISION_COMPARISON_HASHING_STRATEGY)
      changes.associateWithTo(result) { change ->
        val patch = parsedChanges.patchesByChange[change] ?: return@associateWithTo 0
        //TODO: cache?
        positions.count { it.mapToLocation(patch) != null }
      }
    }.modelFlow(cs, thisLogger())

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests: Flow<Unit> = _showDiffRequests.asSharedFlow()

  fun updatesChanges(parsedChanges: GitBranchComparisonResult, commitIndex: Int, changeToSelect: Change? = null) {
    cs.launchNow {
      stateGuard.withLock {
        val commits = parsedChanges.commits
        val commit = commitIndex.takeIf { it >= 0 }?.let { commits[it] }?.sha
        val changes = parsedChanges.getChanges(commit)

        _changesWithCommit.emit(parsedChanges to commit)

        if (changeToSelect == null) {
          _changesSelection.value = ChangesSelection.Fuzzy(changes)
          _updates.emit(Update.WithSelectAll(changes))
        }
        else {
          _changesSelection.value = ChangesSelection.Precise(changes, changeToSelect)
          _updates.emit(Update.WithSelectChange(changes, changeToSelect))
        }
      }
    }
  }

  override fun updateSelectedChanges(selection: ChangesSelection?) {
    cs.launchNow {
      stateGuard.withLock {
        _changesSelection.value = selection
      }
    }
  }

  override fun showDiff() {
    cs.launchNow {
      _showDiffRequests.emit(Unit)
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun createUnresolvedDiscussionsPositionsFlow(mergeRequest: GitLabMergeRequest) = channelFlow {
  withContext(CoroutineName("GitLab Merge Request discussions positions collector")) {
    val discussionsCache = ConcurrentHashMap<String, GitLabNotePosition>()
    val draftNotesCache = ConcurrentHashMap<String, GitLabNotePosition>()
    mergeRequest.discussions.collectLatest { discussions ->
      discussionsCache.clear()
      discussions.forEach { disc ->
        launchNow {
          val positionFlow = disc.firstNote.filterNotNull().flatMapLatest { it.position }
          combine(disc.resolved, positionFlow) { resolved, position ->
            if (resolved) null else position
          }.collectLatest {
            if (it != null) discussionsCache[disc.id] = it else discussionsCache.remove(disc.id)
            send(discussionsCache.values.toList() + draftNotesCache.values.toList())
          }
        }
      }
    }

    mergeRequest.draftNotes.collectLatest { notes ->
      draftNotesCache.clear()
      notes.forEach { note ->
        launchNow {
          note.position.collectLatest {
            if (it != null) draftNotesCache[note.id] = it else draftNotesCache.remove(note.id)
            send(discussionsCache.values.toList() + draftNotesCache.values.toList())
          }
        }
      }
    }
  }
}

private fun GitBranchComparisonResult.getChanges(commit: String?) = commit?.let { changesByCommits[it] } ?: changes
