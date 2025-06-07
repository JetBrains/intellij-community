// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai

import com.intellij.collaboration.util.RefComparisonChange
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * VM representing the review as done by AI.
 */
@ApiStatus.Internal
interface GHPRAIReviewViewModel {
  val isLoading: StateFlow<Boolean>

  fun loadReview()
  fun showDiffFor(change: RefComparisonChange, line: Int? = null)
  fun getAICommentsForDiff(change: RefComparisonChange, diffData: GitTextFilePatchWithHistory): Flow<List<GHPRAICommentViewModel>>
}
