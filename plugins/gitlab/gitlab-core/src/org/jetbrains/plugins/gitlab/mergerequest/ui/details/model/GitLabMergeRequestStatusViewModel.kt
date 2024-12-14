// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJobState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.URLUtil
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.dto.GitLabCiJobDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabPipelineDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

interface GitLabMergeRequestStatusViewModel : CodeReviewStatusViewModel {
  val resolveConflictsVm: GitLabResolveConflictsLocallyViewModel
}

class GitLabMergeRequestStatusViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  gitRepository: GitRepository,
  private val serverPath: GitLabServerPath,
  mergeRequest: GitLabMergeRequest,
) : GitLabMergeRequestStatusViewModel {
  private val cs = parentCs.childScope()

  private val pipeline: SharedFlow<GitLabPipelineDTO?> = mergeRequest.details.map { it.headPipeline }.modelFlow(cs, thisLogger())

  override val hasConflicts: SharedFlow<Boolean?>
    get() = resolveConflictsVm.hasConflicts

  @OptIn(ExperimentalCoroutinesApi::class)
  override val requiredConversationsResolved: SharedFlow<Boolean> = mergeRequest.details.map {
    it.onlyAllowMergeIfAllDiscussionsAreResolved
  }.distinctUntilChanged().flatMapLatest { resolveRequired ->
    if (resolveRequired) {
      mergeRequest.nonEmptyDiscussionsData.map { result ->
        result.getOrNull().orEmpty().any { disc ->
          disc.notes.firstOrNull()?.let {
            !it.system && it.resolvable && !it.resolved
          } ?: false
        }
      }
    }
    else {
      flowOf(false)
    }
  }.modelFlow(cs, thisLogger())

  override val ciJobs: SharedFlow<List<CodeReviewCIJob>> = pipeline.map {
    it?.jobs?.mapNotNull { job -> job.convert() } ?: emptyList()
  }.modelFlow(cs, thisLogger())

  private val _showJobsDetailsRequests = MutableSharedFlow<List<CodeReviewCIJob>>()
  override val showJobsDetailsRequests: SharedFlow<List<CodeReviewCIJob>> = _showJobsDetailsRequests

  override fun showJobsDetails() {
    cs.launchNow {
      val jobs = ciJobs.first()
      _showJobsDetailsRequests.emit(jobs)
    }
  }

  override val resolveConflictsVm: GitLabResolveConflictsLocallyViewModel =
    GitLabResolveConflictsLocallyViewModel(cs, project, serverPath, gitRepository, mergeRequest)

  private fun GitLabCiJobDTO.convert(): CodeReviewCIJob? {
    val jobUrl: String? = detailedStatus?.detailsPath?.let { url ->
      if (URLUtil.canContainUrl(url)) url
      else "$serverPath$url"
    }
    val status = status?.let { it.toCiState() } ?: return null
    val isRequired = allowFailure?.not() ?: true

    return CodeReviewCIJob(name, status, isRequired, jobUrl)
  }

  private fun GitLabCiJobStatus.toCiState(): CodeReviewCIJobState = when (this) {
    GitLabCiJobStatus.CANCELED,
    GitLabCiJobStatus.FAILED,
      -> CodeReviewCIJobState.FAILED

    GitLabCiJobStatus.MANUAL,
    GitLabCiJobStatus.SKIPPED,
      -> CodeReviewCIJobState.SKIPPED

    GitLabCiJobStatus.CREATED,
    GitLabCiJobStatus.PENDING,
    GitLabCiJobStatus.PREPARING,
    GitLabCiJobStatus.RUNNING,
    GitLabCiJobStatus.SCHEDULED,
    GitLabCiJobStatus.WAITING_FOR_RESOURCE,
      -> CodeReviewCIJobState.PENDING

    GitLabCiJobStatus.SUCCESS -> CodeReviewCIJobState.SUCCESS
  }
}
