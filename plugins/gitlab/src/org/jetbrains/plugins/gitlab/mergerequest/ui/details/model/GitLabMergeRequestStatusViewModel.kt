// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJobState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.collaboration.util.resolveRelative
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabCiJobDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabPipelineDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import java.net.URI

class GitLabMergeRequestStatusViewModel(
  parentCs: CoroutineScope,
  mergeRequest: GitLabMergeRequest,
  private val serverPath: GitLabServerPath
) : CodeReviewStatusViewModel {
  private val cs = parentCs.childScope()

  private val pipeline: SharedFlow<GitLabPipelineDTO?> = mergeRequest.details.map { it.headPipeline }.modelFlow(cs, thisLogger())

  override val hasConflicts: SharedFlow<Boolean> = mergeRequest.details.map { it.conflicts }.modelFlow(cs, thisLogger())
  private val requiredPipelineSucceeds: SharedFlow<Boolean> = mergeRequest.details.map { it.onlyAllowMergeIfPipelineSucceeds }.modelFlow(cs,
                                                                                                                                         thisLogger())
  private val allowMergeOnSkippedPipeline: SharedFlow<Boolean> = mergeRequest.details.map { it.allowMergeOnSkippedPipeline }.modelFlow(cs,
                                                                                                                               thisLogger())
  override val requiredConversationsResolved: SharedFlow<Boolean> = mergeRequest.details.map { it.onlyAllowMergeIfAllDiscussionsAreResolved }.modelFlow(
    cs, thisLogger())

  override val ciJobs: SharedFlow<List<CodeReviewCIJob>> = combine(pipeline, requiredPipelineSucceeds,
                                                                   allowMergeOnSkippedPipeline) { it, requiredPipelineSucceeds, allowMergeOnSkippedPipeline ->
    it?.jobs?.map { job -> job.convert(requiredPipelineSucceeds, allowMergeOnSkippedPipeline) } ?: emptyList()
  }.modelFlow(cs, thisLogger())

  private val _showJobsDetailsRequests = MutableSharedFlow<List<CodeReviewCIJob>>()
  override val showJobsDetailsRequests: SharedFlow<List<CodeReviewCIJob>> = _showJobsDetailsRequests

  override fun showJobsDetails() {
    cs.launchNow {
      val jobs = ciJobs.first()
      _showJobsDetailsRequests.emit(jobs)
    }
  }

  private fun GitLabCiJobDTO.convert(requiredPipelineSucceeds: Boolean, allowMergeOnSkippedPipeline: Boolean): CodeReviewCIJob {
    val jobUrl: URI? = webPath?.let { serverPath.toURI().resolveRelative(it) }
    return CodeReviewCIJob(name, status.toCiState(allowMergeOnSkippedPipeline), requiredPipelineSucceeds, jobUrl?.toString())
  }

  // TODO: Add more states (CodeReviewCIJobState.SKIPPED -> MANUAL, SKIPPED)
  private fun GitLabCiJobStatus.toCiState(allowMergeOnSkippedPipeline: Boolean): CodeReviewCIJobState = when (this) {
    GitLabCiJobStatus.CANCELED,
    GitLabCiJobStatus.FAILED,
    GitLabCiJobStatus.MANUAL -> CodeReviewCIJobState.FAILED
    GitLabCiJobStatus.SKIPPED -> if (allowMergeOnSkippedPipeline) CodeReviewCIJobState.SUCCESS else CodeReviewCIJobState.FAILED

    GitLabCiJobStatus.CREATED,
    GitLabCiJobStatus.PENDING,
    GitLabCiJobStatus.PREPARING,
    GitLabCiJobStatus.RUNNING,
    GitLabCiJobStatus.SCHEDULED,
    GitLabCiJobStatus.WAITING_FOR_RESOURCE -> CodeReviewCIJobState.PENDING

    GitLabCiJobStatus.SUCCESS -> CodeReviewCIJobState.SUCCESS
  }
}