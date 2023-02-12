// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject

internal interface GitLabMergeRequestDetailsViewModel {
  val detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel
  val detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel
  val changesVm: GitLabMergeRequestChangesViewModel
}

internal class GitLabMergeRequestDetailsViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  projectData: GitLabProject,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestDetailsViewModel {

  private val cs = parentCs.childScope()

  override val detailsInfoVm = GitLabMergeRequestDetailsInfoViewModelImpl(cs, mergeRequest)
  override val detailsReviewFlowVm = GitLabMergeRequestReviewFlowViewModelImpl(project, cs, currentUser, projectData, mergeRequest)
  override val changesVm = GitLabMergeRequestChangesViewModelImpl(cs, mergeRequest.changes)
}