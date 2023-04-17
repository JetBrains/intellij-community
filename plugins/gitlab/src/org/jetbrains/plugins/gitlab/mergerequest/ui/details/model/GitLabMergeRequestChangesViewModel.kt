// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangesViewModelBase
import com.intellij.openapi.ListSelection
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges
import org.jetbrains.plugins.gitlab.mergerequest.diff.isEqual

internal interface GitLabMergeRequestChangesViewModel : CodeReviewChangesViewModel<GitLabCommitDTO> {
  val changesResult: Flow<Result<Collection<Change>>>

  val userChangesSelection: Flow<ListSelection<Change>>
  val changeSelectionRequests: Flow<Change>

  fun selectChange(change: Change)

  fun updateChangesSelectedByUser(changes: ListSelection<Change>)

  fun showDiff()
}

internal class GitLabMergeRequestChangesViewModelImpl(
  parentCs: CoroutineScope,
  changes: Flow<GitLabMergeRequestChanges>
) : GitLabMergeRequestChangesViewModel,
    CodeReviewChangesViewModelBase<GitLabCommitDTO>() {
  private val cs = parentCs.childScope()

  override val reviewCommits: StateFlow<List<GitLabCommitDTO>> =
    changes.map { it.commits }
      .stateIn(cs, SharingStarted.Lazily, listOf())

  override val changesResult: Flow<Result<Collection<Change>>> =
    combine(changes.map { runCatching { it.getParsedChanges() } }, selectedCommit) { changesResult, commit ->
      changesResult.map {
        it.changesByCommits[commit?.sha] ?: it.changes
      }
    }.modelFlow(cs, thisLogger())

  private val _userChangesSelection = MutableSharedFlow<ListSelection<Change>>()
  override val userChangesSelection: Flow<ListSelection<Change>> = _userChangesSelection.asSharedFlow().distinctUntilChanged { old, new ->
    isSelectionEqual(old, new)
  }

  private val _changeSelectionRequests = MutableSharedFlow<Change>()
  override val changeSelectionRequests: Flow<Change> = _changeSelectionRequests.asSharedFlow()

  override fun selectChange(change: Change) {
    cs.launch {
      _changeSelectionRequests.emit(change)
    }
  }

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests = _showDiffRequests.asSharedFlow()

  override fun commitHash(commit: GitLabCommitDTO): String {
    return commit.shortId
  }

  override fun updateChangesSelectedByUser(changes: ListSelection<Change>) {
    cs.launch {
      _userChangesSelection.emit(changes)
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