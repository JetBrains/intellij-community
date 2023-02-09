// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.details.RequestState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStateModel

internal class GHPRDetailsViewModelImpl(
  scope: CoroutineScope,
  detailsModel: GHPRDetailsModel,
  stateModel: GHPRStateModel
) : GHPRDetailsViewModel {
  private val _titleState: MutableStateFlow<String> = MutableStateFlow(detailsModel.title)
  override val titleState: StateFlow<String> = _titleState.asStateFlow()

  private val _descriptionState: MutableStateFlow<String> = MutableStateFlow(detailsModel.description)
  override val descriptionState: StateFlow<String> = _descriptionState.asStateFlow()

  private val _reviewMergeState: MutableStateFlow<GHPullRequestState> = MutableStateFlow(detailsModel.state)

  private val _isDraftState: MutableStateFlow<Boolean> = MutableStateFlow(stateModel.isDraft)
  override val isDraftState: StateFlow<Boolean> = _isDraftState.asStateFlow()

  override val requestState: Flow<RequestState> = combine(_reviewMergeState, _isDraftState) { reviewMergeState, isDraft ->
    if (isDraft) return@combine RequestState.DRAFT
    return@combine when (reviewMergeState) {
      GHPullRequestState.CLOSED -> RequestState.CLOSED
      GHPullRequestState.MERGED -> RequestState.MERGED
      GHPullRequestState.OPEN -> RequestState.OPENED
    }
  }

  override val number: String = detailsModel.number
  override val url: String = detailsModel.url
  override val viewerDidAuthor: Boolean = stateModel.viewerDidAuthor

  private val _mergeabilityState: MutableStateFlow<GHPRMergeabilityState?> = MutableStateFlow(stateModel.mergeabilityState)
  override val mergeabilityState: StateFlow<GHPRMergeabilityState?> = _mergeabilityState.asStateFlow()

  override val hasConflictsState: StateFlow<Boolean?> = _mergeabilityState.mapState(scope) { mergeability ->
    mergeability?.hasConflicts
  }

  override val isRestrictedState: StateFlow<Boolean> = _mergeabilityState.mapState(scope) { mergeability ->
    mergeability?.isRestricted ?: false
  }

  override val checksState: StateFlow<GHPRMergeabilityState.ChecksState> = _mergeabilityState.mapState(scope) { mergeability ->
    mergeability?.checksState ?: GHPRMergeabilityState.ChecksState.NONE
  }

  override val requiredApprovingReviewsCountState: StateFlow<Int> = _mergeabilityState.mapState(scope) { mergeability ->
    mergeability?.requiredApprovingReviewsCount ?: 0
  }

  init {
    detailsModel.addAndInvokeDetailsChangedListener {
      _titleState.value = detailsModel.title
      _descriptionState.value = detailsModel.description
      _reviewMergeState.value = detailsModel.state
    }

    stateModel.addAndInvokeDraftStateListener {
      _isDraftState.value = stateModel.isDraft
    }

    stateModel.addAndInvokeMergeabilityStateLoadingResultListener {
      _mergeabilityState.value = stateModel.mergeabilityState
    }
  }
}