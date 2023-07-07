// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.asStateFlow
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState

internal class GHPRDetailsViewModel(
  parentCs: CoroutineScope,
  detailsModel: SingleValueModel<GHPullRequest>
) : CodeReviewDetailsViewModel {

  private val cs = parentCs.childScope()

  override val number: String = "#${detailsModel.value.number}"
  override val url: String = detailsModel.value.url

  private val detailsState: StateFlow<GHPullRequest> = detailsModel.asStateFlow()

  override val title: SharedFlow<String> = detailsState.map { it.title }
    .shareIn(cs, SharingStarted.Lazily, 1)

  override val description: SharedFlow<String> = detailsState.map { it.body }
    .shareIn(cs, SharingStarted.Lazily, 1)

  override val reviewRequestState: SharedFlow<ReviewRequestState> = detailsState.map { details ->
    if (details.isDraft) return@map ReviewRequestState.DRAFT
    when (details.state) {
      GHPullRequestState.CLOSED -> ReviewRequestState.CLOSED
      GHPullRequestState.MERGED -> ReviewRequestState.MERGED
      GHPullRequestState.OPEN -> ReviewRequestState.OPENED
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)
}