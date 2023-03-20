// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState

interface GHPRStatusViewModel : CodeReviewStatusViewModel {
  val viewerDidAuthor: Boolean

  val isDraft: Flow<Boolean>
  val mergeabilityState: Flow<GHPRMergeabilityState?>
  val isRestricted: Flow<Boolean>
  val checksState: Flow<GHPRMergeabilityState.ChecksState>
  val requiredApprovingReviewsCount: Flow<Int>
}

class GHPRStatusViewModelImpl(detailsModel: GHPRDetailsModel, stateModel: GHPRStateModel) : GHPRStatusViewModel {
  override val viewerDidAuthor: Boolean = stateModel.viewerDidAuthor

  private val _isDraftState: MutableStateFlow<Boolean> = MutableStateFlow(stateModel.isDraft)
  override val isDraft: Flow<Boolean> = _isDraftState.asSharedFlow()

  private val _mergeabilityState: MutableStateFlow<GHPRMergeabilityState?> = MutableStateFlow(stateModel.mergeabilityState)
  override val mergeabilityState: Flow<GHPRMergeabilityState?> = _mergeabilityState.asSharedFlow()

  override val hasConflicts: Flow<Boolean> = _mergeabilityState.map { mergeability ->
    mergeability?.hasConflicts ?: false
  }

  override val isRestricted: Flow<Boolean> = _mergeabilityState.map { mergeability ->
    mergeability?.isRestricted ?: false
  }

  override val checksState: Flow<GHPRMergeabilityState.ChecksState> = _mergeabilityState.map { mergeability ->
    mergeability?.checksState ?: GHPRMergeabilityState.ChecksState.NONE
  }

  override val requiredApprovingReviewsCount: Flow<Int> = _mergeabilityState.map { mergeability ->
    mergeability?.requiredApprovingReviewsCount ?: 0
  }

  override val hasCI: Flow<Boolean> = _mergeabilityState.map { it != null }
  override val pendingCI: Flow<Int> = _mergeabilityState.map { it?.pendingChecks ?: 0 }
  override val failedCI: Flow<Int> = _mergeabilityState.map { it?.failedChecks ?: 0 }
  override val urlCI: String = "${detailsModel.url}/checks"

  init {
    stateModel.addAndInvokeMergeabilityStateLoadingResultListener {
      _mergeabilityState.value = stateModel.mergeabilityState
    }
  }
}