// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.async.mapState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

  private val _numberState: MutableStateFlow<String> = MutableStateFlow(detailsModel.number)
  override val numberState: StateFlow<String> = _numberState.asStateFlow()

  private val _urlState: MutableStateFlow<String> = MutableStateFlow(detailsModel.url)
  override val urlState: StateFlow<String> = _urlState.asStateFlow()

  private val _descriptionState: MutableStateFlow<String> = MutableStateFlow(detailsModel.description)
  override val descriptionState: StateFlow<String> = _descriptionState.asStateFlow()

  private val _reviewMergeState: MutableStateFlow<GHPullRequestState> = MutableStateFlow(detailsModel.state)
  override val reviewMergeState: StateFlow<GHPullRequestState> = _reviewMergeState.asStateFlow()

  private val _isDraftState: MutableStateFlow<Boolean> = MutableStateFlow(stateModel.isDraft)
  override val isDraftState: StateFlow<Boolean> = _isDraftState.asStateFlow()

  override val viewerDidAuthorState: Boolean = stateModel.viewerDidAuthor

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
      _numberState.value = detailsModel.number
      _urlState.value = detailsModel.url
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