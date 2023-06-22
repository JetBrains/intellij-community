// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel

internal class GHPRDetailsViewModelImpl(
  detailsModel: GHPRDetailsModel,
  stateModel: GHPRStateModel
) : CodeReviewDetailsViewModel {
  private val _titleState: MutableStateFlow<String> = MutableStateFlow(detailsModel.title)
  override val title: Flow<String> = _titleState.asSharedFlow()

  private val _descriptionState: MutableStateFlow<String> = MutableStateFlow(detailsModel.description)
  override val description: Flow<String> = _descriptionState.asSharedFlow()

  private val _reviewMergeState: MutableStateFlow<GHPullRequestState> = MutableStateFlow(detailsModel.state)

  private val _isDraftState: MutableStateFlow<Boolean> = MutableStateFlow(stateModel.isDraft)

  override val reviewRequestState: Flow<ReviewRequestState> = combine(_reviewMergeState, _isDraftState) { reviewMergeState, isDraft ->
    if (isDraft) return@combine ReviewRequestState.DRAFT
    return@combine when (reviewMergeState) {
      GHPullRequestState.CLOSED -> ReviewRequestState.CLOSED
      GHPullRequestState.MERGED -> ReviewRequestState.MERGED
      GHPullRequestState.OPEN -> ReviewRequestState.OPENED
    }
  }

  override val number: String = "#${detailsModel.number}"
  override val url: String = detailsModel.url

  init {
    detailsModel.addAndInvokeDetailsChangedListener {
      _titleState.value = detailsModel.title
      _descriptionState.value = detailsModel.description
      _reviewMergeState.value = detailsModel.state
    }

    stateModel.addAndInvokeDraftStateListener {
      _isDraftState.value = stateModel.isDraft
    }
  }
}