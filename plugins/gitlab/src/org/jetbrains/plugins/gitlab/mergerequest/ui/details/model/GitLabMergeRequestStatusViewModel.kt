// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJobState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.collaboration.util.resolveRelative
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabCiJobDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabPipelineDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import java.net.URI

class GitLabMergeRequestStatusViewModel(
  mergeRequest: GitLabMergeRequest,
  private val serverPath: GitLabServerPath
) : CodeReviewStatusViewModel {
  private val pipeline: Flow<GitLabPipelineDTO?> = mergeRequest.pipeline

  override val hasConflicts: Flow<Boolean> = mergeRequest.hasConflicts

  override val ciJobs: Flow<List<CodeReviewCIJob>> = pipeline.map {
    it?.jobs?.map { job -> job.convert() } ?: emptyList()
  }

  private fun GitLabCiJobDTO.convert(): CodeReviewCIJob {
    val jobUrl: URI = serverPath.toURI().resolveRelative(webPath)
    return CodeReviewCIJob(name, status.toCiState(), jobUrl.toString())
  }

  private fun GitLabCiJobStatus.toCiState(): CodeReviewCIJobState = when (this) {
    GitLabCiJobStatus.CANCELED,
    GitLabCiJobStatus.CREATED,
    GitLabCiJobStatus.MANUAL,
    GitLabCiJobStatus.PREPARING,
    GitLabCiJobStatus.SCHEDULED,
    GitLabCiJobStatus.SKIPPED,
    GitLabCiJobStatus.RUNNING,
    GitLabCiJobStatus.WAITING_FOR_RESOURCE,
    GitLabCiJobStatus.FAILED -> CodeReviewCIJobState.FAILED
    GitLabCiJobStatus.PENDING -> CodeReviewCIJobState.PENDING
    GitLabCiJobStatus.SUCCESS -> CodeReviewCIJobState.SUCCESS
  }
}