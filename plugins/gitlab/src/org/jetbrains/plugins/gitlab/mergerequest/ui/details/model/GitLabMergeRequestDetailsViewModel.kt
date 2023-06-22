// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel

internal interface GitLabMergeRequestDetailsViewModel : CodeReviewDetailsViewModel, GitLabMergeRequestViewModel {
  val isLoading: Flow<Boolean>

  val detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel
  val branchesVm: CodeReviewBranchesViewModel
  val statusVm: CodeReviewStatusViewModel
  val changesVm: GitLabMergeRequestChangesViewModel

  val showTimelineRequests: Flow<Unit>

  fun showTimeline()

  override fun refreshData()
}

internal class GitLabMergeRequestDetailsViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestDetailsViewModel {

  private val cs = parentCs.childScope()

  override val number: String = "!${mergeRequest.number}"
  override val url: String = mergeRequest.url
  override val author: GitLabUserDTO = mergeRequest.author

  override val title: Flow<String> = mergeRequest.title
  override val description: Flow<String> = mergeRequest.description
  override val descriptionHtml: Flow<String> = mergeRequest.descriptionHtml
  override val reviewRequestState: Flow<ReviewRequestState> = mergeRequest.reviewRequestState

  override val isLoading: Flow<Boolean> = mergeRequest.isLoading

  private val _showTimelineRequests = MutableSharedFlow<Unit>()
  override val showTimelineRequests: Flow<Unit> = _showTimelineRequests.asSharedFlow()

  override fun showTimeline() {
    cs.launch {
      _showTimelineRequests.emit(Unit)
    }
  }

  override val detailsReviewFlowVm = GitLabMergeRequestReviewFlowViewModelImpl(project, cs, currentUser, projectData, mergeRequest)
  override val branchesVm = GitLabMergeRequestBranchesViewModel(project, cs, mergeRequest, projectData.projectMapping.remote.repository)
  override val statusVm = GitLabMergeRequestStatusViewModel(mergeRequest, projectData.projectMapping.repository.serverPath)
  override val changesVm = GitLabMergeRequestChangesViewModelImpl(cs, mergeRequest)

  override fun refreshData() {
    cs.launch {
      mergeRequest.refreshData()
    }
  }
}