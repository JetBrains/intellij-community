// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortRestDTO

internal class GitLabReviewTabsController {
  private val _openReviewTabRequest = MutableSharedFlow<GitLabReviewTab>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val openReviewTabRequest: Flow<GitLabReviewTab> = _openReviewTabRequest

  fun openReviewDetails(reviewDto: GitLabMergeRequestShortRestDTO) {
    _openReviewTabRequest.tryEmit(GitLabReviewTab.ReviewSelected(reviewDto))
  }
}


internal sealed class GitLabReviewTab {
  object ReviewList : GitLabReviewTab()
  data class ReviewSelected(val reviewDto: GitLabMergeRequestShortRestDTO) : GitLabReviewTab()
}