// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.collaboration.ui.codereview.issues.processIssueIdsHtml
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.mergerequest.data.reviewState
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil

internal interface GitLabMergeRequestDetailsViewModel : CodeReviewDetailsViewModel, GitLabMergeRequestViewModel {
  val isLoading: Flow<Boolean>

  val detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel
  val branchesVm: CodeReviewBranchesViewModel
  val statusVm: GitLabMergeRequestStatusViewModel
  val changesVm: GitLabMergeRequestChangesViewModel

  val showTimelineRequests: Flow<Unit>

  fun showTimeline()

  override fun refreshData()
}

private val LOG = logger<GitLabMergeRequestDetailsViewModel>()

internal class GitLabMergeRequestDetailsViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
) : GitLabMergeRequestDetailsViewModel {

  private val cs = parentCs.childScope()

  override val number: String = "!${mergeRequest.iid}"
  override val url: String = mergeRequest.url
  override val author: GitLabUserDTO = mergeRequest.author

  override val title: SharedFlow<String> = mergeRequest.details.map { it.title }.map { title ->
    GitLabUIUtil.convertToHtml(project, mergeRequest.gitRepository, mergeRequest.glProject.projectPath, title)
  }.modelFlow(cs, LOG)
  override val description: SharedFlow<String> = mergeRequest.details.map { it.description }.map { description ->
    processIssueIdsHtml(project, description)
  }.modelFlow(cs, LOG)
  override val descriptionHtml: SharedFlow<String> = mergeRequest.details.map { it.description }.map {
    if (it.isNotBlank()) GitLabUIUtil.convertToHtml(project, mergeRequest.gitRepository, mergeRequest.glProject.projectPath, it) else it
  }.modelFlow(cs, LOG)
  override val reviewRequestState: SharedFlow<ReviewRequestState> = mergeRequest.details.map { it.reviewState }
    .modelFlow(cs, LOG)

  override val isLoading: Flow<Boolean> = mergeRequest.isLoading

  private val _showTimelineRequests = MutableSharedFlow<Unit>()
  override val showTimelineRequests: Flow<Unit> = _showTimelineRequests.asSharedFlow()

  override fun showTimeline() {
    cs.launch {
      _showTimelineRequests.emit(Unit)
    }
  }

  override val detailsReviewFlowVm = GitLabMergeRequestReviewFlowViewModelImpl(
    project, cs, currentUser, projectData, mergeRequest, avatarIconsProvider
  )
  override val branchesVm = GitLabMergeRequestBranchesViewModel(cs, mergeRequest, projectData.projectMapping)
  override val statusVm = GitLabMergeRequestStatusViewModelImpl(project, cs, projectData.projectMapping.gitRepository,
                                                                projectData.projectMapping.repository.serverPath, mergeRequest)
  override val changesVm = GitLabMergeRequestChangesViewModelImpl(project, cs, mergeRequest)

  override fun reloadData() {
    cs.launchNow {
      mergeRequest.reloadData()
    }
  }

  override fun refreshData() {
    cs.launch {
      mergeRequest.refreshData()
    }
  }
}