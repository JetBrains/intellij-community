// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.ai.assistedReview.GHPRAiAssistantToolwindowViewModel
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.mergeabilityStateComputationFlow

interface GHPRStatusViewModel : CodeReviewStatusViewModel {
  val viewerDidAuthor: Boolean

  val isDraft: Flow<Boolean>
  val mergeabilityState: Flow<GHPRMergeabilityState?>
  val isRestricted: Flow<Boolean>
  val requiredApprovingReviewsCount: Flow<Int>

  val resolveConflictsVm: ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError>

  fun requestAiReview()
}

private val LOG = logger<GHPRStatusViewModel>()

class GHPRStatusViewModelImpl internal constructor(
  parentCs: CoroutineScope,
  private val project: Project,
  server: GithubServerPath,
  gitRepository: GitRepository,
  private val dataProvider: GHPRDataProvider,
  detailsState: StateFlow<GHPullRequest>,
) : GHPRStatusViewModel {
  private val cs = parentCs.childScope()

  override val viewerDidAuthor: Boolean = detailsState.value.viewerDidAuthor

  override val isDraft: Flow<Boolean> = detailsState.map { it.isDraft }
    .modelFlow(cs, LOG)

  private val detailsData = dataProvider.detailsData
  override val mergeabilityState: Flow<GHPRMergeabilityState?> =
    detailsData.mergeabilityStateComputationFlow.mapNotNull { it.getOrNull() }
      .modelFlow(cs, LOG)

  override val hasConflicts: SharedFlow<Boolean?>
    get() = resolveConflictsVm.hasConflicts
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

  override val resolveConflictsVm: ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError> =
    GHPRResolveConflictsLocallyViewModelImpl(cs, project, server, gitRepository, detailsData)

  override fun requestAiReview() {
    project.service<GHPRAiAssistantToolwindowViewModel>().requestReview(dataProvider)
  }
}
