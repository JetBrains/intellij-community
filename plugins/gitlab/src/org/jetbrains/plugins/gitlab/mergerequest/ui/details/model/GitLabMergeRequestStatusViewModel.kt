// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabCiJobDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabPipelineDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

class GitLabMergeRequestStatusViewModel(mergeRequest: GitLabMergeRequest) : CodeReviewStatusViewModel {
  private val pipeline: Flow<GitLabPipelineDTO?> = mergeRequest.pipeline
  private val ciJobs: Flow<List<GitLabCiJobDTO>> = pipeline.map { it?.jobs ?: emptyList() }
  private val targetProject: StateFlow<GitLabProjectDTO> = mergeRequest.targetProject

  override val hasConflicts: Flow<Boolean> = mergeRequest.hasConflicts
  override val hasCI: Flow<Boolean> = ciJobs.map { it.isNotEmpty() }
  override val pendingCI: Flow<Int> = ciJobs.map { jobs -> jobs.count { job -> job.status == GitLabCiJobStatus.PENDING } }
  override val failedCI: Flow<Int> = ciJobs.map { jobs -> jobs.count { job -> job.status == GitLabCiJobStatus.FAILED } }
  override val urlCI: String = "${targetProject.value.webUrl}/-/jobs"
}