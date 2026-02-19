// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffProcessorViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.PreLoadingCodeReviewAsyncDiffViewModelDelegate
import com.intellij.collaboration.ui.codereview.diff.model.RefComparisonChangesSorter
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings

@ApiStatus.Internal
class GHPRCreateDiffViewModel(private val project: Project, parentCs: CoroutineScope) : CodeReviewDiffProcessorViewModel<GHPRCreateDiffChangeViewModel> {
  private val cs = parentCs.childScope(javaClass.name)

  private val changesSorter = GithubPullRequestsProjectUISettings.getInstance(project).changesGroupingState
    .map { groupings ->
      { changes: List<RefComparisonChange> -> RefComparisonChangesSorter.Grouping(project, groupings).sort(changes) }
    }

  private val delegate = PreLoadingCodeReviewAsyncDiffViewModelDelegate.create(flowOf(ComputedResult.success(Unit)), changesSorter) { _, change ->
    GHPRCreateDiffChangeViewModel(project, cs, change)
  }

  suspend fun handleSelection(listener: (ListSelection<RefComparisonChange>?) -> Unit): Nothing {
    delegate.handleSelection(listener)
  }

  fun showChanges(changes: ListSelection<RefComparisonChange>, scrollRequest: DiffViewerScrollRequest? = null) {
    delegate.showChanges(changes, scrollRequest)
  }

  override val changes: StateFlow<ComputedResult<CodeReviewDiffProcessorViewModel.State<GHPRCreateDiffChangeViewModel>>?> =
    delegate.changes.stateIn(cs, SharingStarted.Eagerly, null)

  override fun showChange(change: GHPRCreateDiffChangeViewModel, scrollRequest: DiffViewerScrollRequest?) {
    delegate.showChange(change.change, scrollRequest)
  }

  override fun showChange(changeIdx: Int, scrollRequest: DiffViewerScrollRequest?) {
    val changeVm = changes.value?.result?.getOrNull()?.selectedChanges?.list?.getOrNull(changeIdx) ?: return
    showChange(changeVm, scrollRequest)
  }
}