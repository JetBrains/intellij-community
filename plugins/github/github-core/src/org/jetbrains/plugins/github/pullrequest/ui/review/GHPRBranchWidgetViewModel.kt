// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.util.ComputedResult
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.changesComputationState
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewBranchStateSharedViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel

interface GHPRBranchWidgetViewModel : GHPRReviewViewModel {
  val id: GHPRIdentifier

  val updateRequired: StateFlow<Boolean>
  val dataLoadingState: StateFlow<ComputedResult<Any>>
  val editorReviewEnabled: StateFlow<Boolean>

  val updateErrors: SharedFlow<Exception>

  fun showPullRequest()
  fun updateBranch()
  fun toggleEditorReview()
}

internal class GHPRBranchWidgetViewModelImpl(
  parentCs: CoroutineScope,
  private val settings: GithubPullRequestsProjectUISettings,
  dataProvider: GHPRDataProvider,
  private val projectVm: GHPRToolWindowProjectViewModel,
  private val sharedBranchVm: GHPRReviewBranchStateSharedViewModel,
  private val reviewVmHelper: GHPRReviewViewModelHelper,
  override val id: GHPRIdentifier
) : GHPRBranchWidgetViewModel, GHPRReviewViewModel by DelegatingGHPRReviewViewModel(reviewVmHelper) {
  private val cs = parentCs.childScope(javaClass.name)

  override val updateRequired: StateFlow<Boolean> = sharedBranchVm.updateRequired

  override val dataLoadingState: StateFlow<ComputedResult<Any>> =
    dataProvider.changesData.changesComputationState().stateInNow(cs, ComputedResult.loading())

  override val editorReviewEnabled: StateFlow<Boolean> = settings.editorReviewEnabledState

  override val updateErrors: SharedFlow<Exception> = sharedBranchVm.updateErrors

  override fun showPullRequest() {
    projectVm.viewPullRequest(id)
  }

  override fun updateBranch() = sharedBranchVm.updateBranch()

  override fun toggleEditorReview() {
    settings.editorReviewEnabled = !settings.editorReviewEnabled
  }
}
