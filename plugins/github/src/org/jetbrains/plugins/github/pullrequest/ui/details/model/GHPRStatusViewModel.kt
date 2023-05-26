// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
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
  val requiredApprovingReviewsCount: Flow<Int>
}

class GHPRStatusViewModelImpl(stateModel: GHPRStateModel) : GHPRStatusViewModel {
  override val viewerDidAuthor: Boolean = stateModel.viewerDidAuthor

  private val _isDraftState: MutableStateFlow<Boolean> = MutableStateFlow(stateModel.isDraft)
  override val isDraft: Flow<Boolean> = _isDraftState.asSharedFlow()

  private val _mergeabilityState: MutableStateFlow<GHPRMergeabilityState?> = MutableStateFlow(stateModel.mergeabilityState)
  override val mergeabilityState: Flow<GHPRMergeabilityState?> = _mergeabilityState.asSharedFlow()

  override val hasConflicts: Flow<Boolean> = _mergeabilityState.map { mergeability ->
    mergeability?.hasConflicts ?: false
  }
  override val ciJobs: Flow<List<CodeReviewCIJob>> = _mergeabilityState.map { it?.ciJobs ?: emptyList() }

  override val isRestricted: Flow<Boolean> = _mergeabilityState.map { mergeability ->
    mergeability?.isRestricted ?: false
  }

  override val requiredApprovingReviewsCount: Flow<Int> = _mergeabilityState.map { mergeability ->
    mergeability?.requiredApprovingReviewsCount ?: 0
  }

  init {
    stateModel.addAndInvokeMergeabilityStateLoadingResultListener {
      _mergeabilityState.value = stateModel.mergeabilityState
    }
  }
}