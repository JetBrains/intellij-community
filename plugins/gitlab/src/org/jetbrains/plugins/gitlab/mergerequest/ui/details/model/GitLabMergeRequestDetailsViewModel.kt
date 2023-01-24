// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal interface GitLabMergeRequestDetailsViewModel {
  val detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel
  val detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel
}

internal class GitLabMergeRequestDetailsViewModelImpl(
  scope: CoroutineScope,
  currentUser: GitLabUserDTO,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestDetailsViewModel {
  override val detailsInfoVm = GitLabMergeRequestDetailsInfoViewModelImpl(mergeRequest)
  override val detailsReviewFlowVm = GitLabMergeRequestReviewFlowViewModelImpl(scope, currentUser, mergeRequest)
}