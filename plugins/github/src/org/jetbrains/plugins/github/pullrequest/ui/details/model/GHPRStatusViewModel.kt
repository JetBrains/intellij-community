// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRStateDataProvider

interface GHPRStatusViewModel : CodeReviewStatusViewModel {
  val viewerDidAuthor: Boolean

  val isDraft: Flow<Boolean>
  val mergeabilityState: Flow<GHPRMergeabilityState?>
  val isRestricted: Flow<Boolean>
  val requiredApprovingReviewsCount: Flow<Int>
}

private val LOG = logger<GHPRStatusViewModel>()

class GHPRStatusViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  detailsState: StateFlow<GHPullRequest>,
  stateData: GHPRStateDataProvider
) : GHPRStatusViewModel {
  private val cs = parentCs.childScope()

  override val viewerDidAuthor: Boolean = detailsState.value.viewerDidAuthor

  override val isDraft: Flow<Boolean> = detailsState.map { it.isDraft }
    .modelFlow(cs, LOG)

  override val mergeabilityState: Flow<GHPRMergeabilityState?> = stateData.mergeabilityState.map { it.getOrNull() }
    .modelFlow(cs, LOG)

  override val hasConflicts: SharedFlow<Boolean> = mergeabilityState.map { mergeability ->
    mergeability?.hasConflicts ?: false
  }.modelFlow(cs, LOG)
  override val ciJobs: SharedFlow<List<CodeReviewCIJob>> =
    mergeabilityState.map { it?.ciJobs ?: emptyList() }
      .modelFlow(cs, LOG)

  override val isRestricted: Flow<Boolean> = mergeabilityState.map { mergeability ->
    mergeability?.isRestricted ?: false
  }

  override val requiredApprovingReviewsCount: Flow<Int> = mergeabilityState.map { mergeability ->
    mergeability?.requiredApprovingReviewsCount ?: 0
  }
  // TODO: Implement after switching to version 3.2
  override val requiredConversationsResolved: SharedFlow<Boolean> = flowOf(false)
    .modelFlow(cs, LOG)

  private val _showJobsDetailsRequests = MutableSharedFlow<List<CodeReviewCIJob>>()
  override val showJobsDetailsRequests: SharedFlow<List<CodeReviewCIJob>> = _showJobsDetailsRequests

  override fun showJobsDetails() {
    cs.launchNow {
      val jobs = ciJobs.first()
      _showJobsDetailsRequests.emit(jobs)
      GHPRStatisticsCollector.logDetailsChecksOpened(project)
    }
  }
}