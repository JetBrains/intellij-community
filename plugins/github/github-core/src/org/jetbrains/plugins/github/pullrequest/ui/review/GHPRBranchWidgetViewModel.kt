// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.ui.branch.GitCurrentBranchPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.changesComputationState
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewBranchStateSharedViewModel

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
  project: Project,
  parentCs: CoroutineScope,
  private val settings: GithubPullRequestsProjectUISettings,
  dataProvider: GHPRDataProvider,
  private val sharedBranchVm: GHPRReviewBranchStateSharedViewModel,
  private val reviewVmHelper: GHPRReviewViewModelHelper,
  override val id: GHPRIdentifier,
  private val viewPullRequest: (GHPRIdentifier) -> Unit
) : GHPRBranchWidgetViewModel, GHPRReviewViewModel by DelegatingGHPRReviewViewModel(reviewVmHelper) {
  private val cs = parentCs.childScope(javaClass.name)

  override val dataLoadingState: StateFlow<ComputedResult<Any>> =
    dataProvider.changesData.changesComputationState().stateInNow(cs, ComputedResult.loading())

  init {
    cs.launch {
      dataLoadingState.collect {
        project.messageBus.syncPublisher(GitCurrentBranchPresenter.PRESENTATION_UPDATED).presentationUpdated()
      }
    }
  }

  override val updateRequired: StateFlow<Boolean> = sharedBranchVm.updateRequired

  override val editorReviewEnabled: StateFlow<Boolean> = settings.editorReviewEnabledState

  override val updateErrors: SharedFlow<Exception> = sharedBranchVm.updateErrors

  override fun showPullRequest() {
    viewPullRequest(id)
  }

  override fun updateBranch() = sharedBranchVm.updateBranch()

  override fun toggleEditorReview() {
    settings.editorReviewEnabled = !settings.editorReviewEnabled
  }
}
