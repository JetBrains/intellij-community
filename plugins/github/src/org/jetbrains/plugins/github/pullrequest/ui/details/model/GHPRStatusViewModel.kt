// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState

interface GHPRStatusViewModel : CodeReviewStatusViewModel {
  val viewerDidAuthor: Boolean

  val isDraft: Flow<Boolean>
  val mergeabilityState: Flow<GHPRMergeabilityState?>
  val isRestricted: Flow<Boolean>
  val requiredApprovingReviewsCount: Flow<Int>
}

class GHPRStatusViewModelImpl(parentCs: CoroutineScope, stateModel: GHPRStateModel) : GHPRStatusViewModel {
  private val cs = parentCs.childScope()

  override val viewerDidAuthor: Boolean = stateModel.viewerDidAuthor

  private val _isDraftState: MutableStateFlow<Boolean> = MutableStateFlow(stateModel.isDraft)
  override val isDraft: Flow<Boolean> = _isDraftState.asSharedFlow()

  private val _mergeabilityState: MutableStateFlow<GHPRMergeabilityState?> = MutableStateFlow(stateModel.mergeabilityState)
  override val mergeabilityState: Flow<GHPRMergeabilityState?> = _mergeabilityState.asSharedFlow()

  override val hasConflicts: SharedFlow<Boolean> = _mergeabilityState.map { mergeability ->
    mergeability?.hasConflicts ?: false
  }.modelFlow(cs, thisLogger())
  override val ciJobs: SharedFlow<List<CodeReviewCIJob>> =
    _mergeabilityState.map { it?.ciJobs ?: emptyList() }.modelFlow(cs, thisLogger())

  override val isRestricted: Flow<Boolean> = _mergeabilityState.map { mergeability ->
    mergeability?.isRestricted ?: false
  }

  override val requiredApprovingReviewsCount: Flow<Int> = _mergeabilityState.map { mergeability ->
    mergeability?.requiredApprovingReviewsCount ?: 0
  }

  private val _showJobsDetailsRequests = MutableSharedFlow<List<CodeReviewCIJob>>()
  override val showJobsDetailsRequests: SharedFlow<List<CodeReviewCIJob>> = _showJobsDetailsRequests

  init {
    stateModel.addAndInvokeMergeabilityStateLoadingResultListener {
      _mergeabilityState.value = stateModel.mergeabilityState
    }
  }

  override fun showJobsDetails() {
    cs.launchNow {
      val jobs = ciJobs.first()
      _showJobsDetailsRequests.emit(jobs)
    }
  }
}