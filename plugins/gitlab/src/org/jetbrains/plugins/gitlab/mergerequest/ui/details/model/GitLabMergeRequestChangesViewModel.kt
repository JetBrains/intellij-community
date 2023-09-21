// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesContainer
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelDelegate
import com.intellij.collaboration.util.CODE_REVIEW_CHANGE_HASHING_STRATEGY
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import com.intellij.util.containers.CollectionFactory
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import java.util.concurrent.ConcurrentHashMap

internal interface GitLabMergeRequestChangesViewModel : CodeReviewChangesViewModel<GitLabCommit> {
  /**
   * View model of a current change list
   */
  val changeListVm: SharedFlow<Result<GitLabMergeRequestChangeListViewModel>>

  /**
   * Discussions data for current [changeListVm]
   * Can be slightly out of sync
   */
  val mappedDiscussionsCounts: SharedFlow<Map<Change, Int>>

  fun selectChange(change: Change)
}

private val LOG = logger<GitLabMergeRequestChangesViewModel>()

internal class GitLabMergeRequestChangesViewModelImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestChangesViewModel,
    CodeReviewChangesViewModel<GitLabCommit> {
  private val cs = parentCs.childScope()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val changesContainer: Flow<Result<CodeReviewChangesContainer>> = mergeRequest.changes.mapLatest {
    runCatchingUser {
      val changes = it.getParsedChanges()
      CodeReviewChangesContainer(changes.changes, changes.commits.map { it.sha }, changes.changesByCommits)
    }
  }

  private val delegate = CodeReviewChangesViewModelDelegate(cs, changesContainer) {
    GitLabMergeRequestChangeListViewModelImpl(project, this)
  }

  override val reviewCommits: SharedFlow<List<GitLabCommit>> =
    mergeRequest.changes.map { it.commits }.modelFlow(cs, LOG)

  override val selectedCommitIndex: SharedFlow<Int> = reviewCommits.combine(delegate.selectedCommit) { commits, sha ->
    if (sha == null) -1
    else commits.indexOfFirst { it.sha == sha }
  }.modelFlow(cs, LOG)

  override val selectedCommit: SharedFlow<GitLabCommit?> = reviewCommits.combine(selectedCommitIndex) { commits, index ->
    index.takeIf { it >= 0 }?.let { commits[it] }
  }.modelFlow(cs, LOG)

  override val changeListVm: SharedFlow<Result<GitLabMergeRequestChangeListViewModelImpl>> = delegate.changeListVm

  override val mappedDiscussionsCounts: SharedFlow<Map<Change, Int>> =
    combine(createUnresolvedDiscussionsPositionsFlow(mergeRequest),
            mergeRequest.changes.map { it.getParsedChanges() }.catch { },
            delegate.selectedCommit) { positions, parsedChanges, commit ->
      val changes = parsedChanges.getChanges(commit)
      val result: MutableMap<Change, Int> = CollectionFactory.createCustomHashingStrategyMap(CODE_REVIEW_CHANGE_HASHING_STRATEGY)
      changes.associateWithTo(result) { change ->
        val patch = parsedChanges.patchesByChange[change] ?: return@associateWithTo 0
        //TODO: cache?
        positions.count { it.mapToLocation(patch) != null }
      }
    }.modelFlow(cs, thisLogger())

  override fun selectCommit(index: Int) {
    delegate.selectCommit(index)
  }

  override fun selectNextCommit() {
    delegate.selectNextCommit()
  }

  override fun selectPreviousCommit() {
    delegate.selectPreviousCommit()
  }

  override fun selectChange(change: Change) {
    delegate.selectChange(change)
  }

  override fun commitHash(commit: GitLabCommit): String = commit.shortId
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun createUnresolvedDiscussionsPositionsFlow(mergeRequest: GitLabMergeRequest) = channelFlow {
  withContext(CoroutineName("GitLab Merge Request discussions positions collector")) {
    val discussionsCache = ConcurrentHashMap<String, GitLabNotePosition>()
    val draftNotesCache = ConcurrentHashMap<String, GitLabNotePosition>()
    mergeRequest.discussions.collectLatest { discussionsResult ->
      coroutineScope {
        discussionsCache.clear()
        discussionsResult.getOrNull()?.forEach { disc ->
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
    }

    mergeRequest.draftNotes.collectLatest { notesResult ->
      coroutineScope {
        draftNotesCache.clear()
        notesResult.getOrNull()?.forEach { note ->
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
}

private fun GitBranchComparisonResult.getChanges(commit: String?) = commit?.let { changesByCommits[it] } ?: changes

