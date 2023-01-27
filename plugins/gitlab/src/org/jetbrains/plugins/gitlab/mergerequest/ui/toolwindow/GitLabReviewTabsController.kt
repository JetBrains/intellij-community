// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.toolwindow.ReviewTabsController
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

internal class GitLabReviewTabsController(private val project: Project) : ReviewTabsController<GitLabReviewTab> {
  // TODO: make it more safe, so repository should be passed to places properly where needed
  val currentRepository: GitLabProjectCoordinates?
    get() = project.service<GitLabProjectConnectionManager>().connectionState.value?.repo?.repository

  private val _openReviewTabRequest = MutableSharedFlow<GitLabReviewTab>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val openReviewTabRequest: Flow<GitLabReviewTab> = _openReviewTabRequest

  override val closeReviewTabRequest: Flow<GitLabReviewTab> = emptyFlow() // GitLab are not closed externally (only by toolwindow functionality)

  fun openReviewDetails(reviewId: GitLabMergeRequestId) {
    _openReviewTabRequest.tryEmit(GitLabReviewTab.ReviewSelected(reviewId))
  }
}