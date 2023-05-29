// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject

internal interface GitLabMergeRequestDetailsViewModel {
  val isLoading: Flow<Boolean>

  val detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel
  val detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel
  val statusVm: CodeReviewStatusViewModel
  val changesVm: GitLabMergeRequestChangesViewModel

  val repository: GitRepository
}

internal class GitLabMergeRequestDetailsViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  projectData: GitLabProject,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestDetailsViewModel {

  private val cs = parentCs.childScope()

  override val isLoading: Flow<Boolean> = mergeRequest.isLoading

  override val detailsInfoVm = GitLabMergeRequestDetailsInfoViewModelImpl(cs, mergeRequest)
  override val detailsReviewFlowVm = GitLabMergeRequestReviewFlowViewModelImpl(project, cs, currentUser, projectData, mergeRequest)
  override val statusVm = GitLabMergeRequestStatusViewModel(mergeRequest, projectData.projectMapping.repository.serverPath)
  override val changesVm = GitLabMergeRequestChangesViewModelImpl(cs, mergeRequest.changes)

  override val repository = projectData.projectMapping.remote.repository
}