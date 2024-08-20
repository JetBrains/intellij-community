// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.ui.codereview.diff.CodeReviewDiffRequestProducer
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffViewModelComputer
import com.intellij.collaboration.ui.codereview.diff.model.ComputedDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffProducersViewModel
import com.intellij.collaboration.ui.codereview.diff.model.RefComparisonChangesSorter
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.createVcsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings

internal class GHPRCreateDiffViewModel(private val project: Project, parentCs: CoroutineScope) : ComputedDiffViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  private val changesSorter = GithubPullRequestsProjectUISettings.getInstance(project).changesGroupingState
    .map { RefComparisonChangesSorter.Grouping(project, it) }
  private val helper =
    CodeReviewDiffViewModelComputer(flowOf(ComputedResult.success(Unit)), changesSorter) { _, change ->
      val changeDiffProducer = ChangeDiffRequestProducer.create(project, change.createVcsChange(project))
                               ?: error("Could not create diff producer from $change")
      CodeReviewDiffRequestProducer(project, change, changeDiffProducer, null)
    }

  override val diffVm: StateFlow<ComputedResult<DiffProducersViewModel?>> =
    helper.diffVm.stateIn(cs, SharingStarted.Eagerly, ComputedResult.loading())

  fun showChanges(changes: ChangesSelection) {
    helper.tryShowChanges(changes)
  }
}