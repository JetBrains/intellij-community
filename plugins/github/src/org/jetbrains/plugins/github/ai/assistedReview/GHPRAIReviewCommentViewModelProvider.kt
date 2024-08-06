// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRAICommentViewModelProvider

class GHPRAIReviewCommentViewModelProvider : GHPRAICommentViewModelProvider {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getComments(
    project: Project,
    dataProvider: GHPRDataProvider,
    change: RefComparisonChange,
    diffData: GitTextFilePatchWithHistory,
  ): Flow<List<GHPRAICommentViewModel>> =
    project.service<GHPRAIReviewToolwindowViewModel>().requestedReview
      .flatMapLatest { reviewVmOrNull ->
        val reviewVm = reviewVmOrNull ?: return@flatMapLatest flowOf(listOf())
        reviewVm.getAICommentsForDiff(change, diffData)
      }
}
