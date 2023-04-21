// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewTabsController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

internal class GitLabReviewTabsController : ReviewTabsController<GitLabReviewTab> {
  private val _openReviewTabRequest = MutableSharedFlow<GitLabReviewTab>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val openReviewTabRequest: Flow<GitLabReviewTab> = _openReviewTabRequest

  override val closeReviewTabRequest: Flow<GitLabReviewTab> = emptyFlow() // GitLab are not closed externally (only by toolwindow functionality)

  fun openReviewDetails(reviewId: GitLabMergeRequestId) {
    _openReviewTabRequest.tryEmit(GitLabReviewTab.ReviewSelected(reviewId))
  }
}