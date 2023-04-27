// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal interface GitLabMergeRequestDetailsInfoViewModel : CodeReviewDetailsViewModel {
  val mergeRequest: GitLabMergeRequest

  val targetBranch: Flow<String>
  val sourceBranch: Flow<String>

  val showTimelineRequests: Flow<Unit>

  fun showTimeline()
}

internal class GitLabMergeRequestDetailsInfoViewModelImpl(
  parentCs: CoroutineScope,
  override val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestDetailsInfoViewModel {

  private val cs = parentCs.childScope()

  override val number: String = "!${mergeRequest.number}"
  override val url: String = mergeRequest.url

  override val title: Flow<String> = mergeRequest.title
  override val description: Flow<String> = mergeRequest.description
  override val targetBranch: Flow<String> = mergeRequest.targetBranch
  override val sourceBranch: Flow<String> = mergeRequest.sourceBranch
  override val reviewRequestState: Flow<ReviewRequestState> = mergeRequest.reviewRequestState

  private val _showTimelineRequests = MutableSharedFlow<Unit>()
  override val showTimelineRequests: Flow<Unit> = _showTimelineRequests.asSharedFlow()

  override fun showTimeline() {
    cs.launch {
      _showTimelineRequests.emit(Unit)
    }
  }
}