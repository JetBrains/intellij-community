// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelBase
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import com.intellij.util.containers.CollectionFactory
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.firstNote
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.diff.isEqual
import java.util.concurrent.ConcurrentHashMap

internal interface GitLabMergeRequestChangesViewModel : CodeReviewChangesViewModel<GitLabCommitDTO> {
  val changesResult: Flow<Result<Collection<Change>>>

  val changesSelection: StateFlow<ListSelection<Change>>
  val changeSelectionRequests: Flow<Change>

  val mappedDiscussionsCounts: Flow<Map<Change, Int>>

  fun selectChange(change: Change)

  fun updatesSelectedChanges(changes: ListSelection<Change>)

  fun showDiff()

  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestChangesViewModel>("GitLab.MergeRequest.Changes.ViewModel")
  }
}

internal class GitLabMergeRequestChangesViewModelImpl(
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestChangesViewModel,
    CodeReviewChangesViewModelBase<GitLabCommitDTO>() {
  private val cs = parentCs.childScope()

  override val reviewCommits: StateFlow<List<GitLabCommitDTO>> =
    mergeRequest.changes.map { it.commits }
      .stateIn(cs, SharingStarted.Lazily, listOf())

  private val parsedChanges = mergeRequest.changes.map { runCatching { it.getParsedChanges() } }
    .modelFlow(cs, thisLogger())
  override val changesResult: Flow<Result<Collection<Change>>> =
    combine(parsedChanges, selectedCommit) { changesResult, commit ->
      changesResult.map {
        it.changesByCommits[commit?.sha] ?: it.changes
      }
    }.modelFlow(cs, thisLogger())

  private val _userChangesSelection = MutableStateFlow<ListSelection<Change>>(ListSelection.empty())
  override val changesSelection: StateFlow<ListSelection<Change>> = _userChangesSelection.asStateFlow()

  private val _changeSelectionRequests = MutableSharedFlow<Change>()
  override val changeSelectionRequests: Flow<Change> = _changeSelectionRequests.asSharedFlow()


  private val unresolvedDiscussionsPositions: Flow<Collection<GitLabNotePosition>> = createUnresolvedDiscussionsPositionsFlow(mergeRequest)

  override val mappedDiscussionsCounts: Flow<Map<Change, Int>> =
    combine(parsedChanges, selectedCommit) { changesResult, commit ->
      val diffResult = changesResult.getOrNull() ?: return@combine emptyMap()
      (diffResult.changesByCommits[commit?.sha] ?: diffResult.changes).associateWith {
        diffResult.patchesByChange[it]!!
      }
    }.combine(unresolvedDiscussionsPositions) { changes, positions ->
      val result: MutableMap<Change, Int> =
        CollectionFactory.createCustomHashingStrategyMap(GitBranchComparisonResult.REVISION_COMPARISON_HASHING_STRATEGY)
      changes.mapValuesTo(result) { (_, patch) ->
        //TODO: cache?
        positions.count { it.mapToLocation(patch) != null }
      }
      result
    }.modelFlow(cs, thisLogger())

  override fun selectChange(change: Change) {
    cs.launch {
      val commitIndex = combine(reviewCommits, parsedChanges) { commits, changesRes ->
        val changes = changesRes.getOrNull() ?: throw CancellationException("Missing changes")
        if (changes.changes.find { it.isEqual(change) } != null) {
          -1
        }
        else {
          changes.commitByChange[change]?.let { commitSha -> commits.indexOfFirst { it.sha == commitSha } } ?: -1
        }
      }.first()
      selectCommit(commitIndex)
      _changeSelectionRequests.emit(change)
    }
  }

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests = _showDiffRequests.asSharedFlow()

  override fun commitHash(commit: GitLabCommitDTO): String {
    return commit.shortId
  }

  override fun updatesSelectedChanges(changes: ListSelection<Change>) {
    _userChangesSelection.update {
      if (isSelectionEqual(it, changes)) it else changes
    }
  }

  override fun showDiff() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }

  companion object {
    private fun isSelectionEqual(old: ListSelection<Change>, new: ListSelection<Change>): Boolean {
      if (old.selectedIndex != new.selectedIndex) return false
      if (old.isExplicitSelection != new.isExplicitSelection) return false
      val oldList = old.list
      val newList = new.list
      if (oldList.size != newList.size) return false

      return oldList.isEqual(newList)
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
