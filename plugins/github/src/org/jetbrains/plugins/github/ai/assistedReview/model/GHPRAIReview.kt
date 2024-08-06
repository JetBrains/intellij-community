// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview.model

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.ml.llm.core.grazieAPI.tasks.vcs.reviewBuddy.ReviewBuddyFileData
import com.intellij.openapi.vcs.FilePath
import kotlinx.coroutines.flow.StateFlow

data class ReviewFileAIData(
  val path: FilePath,
  val rawLocalPath: String,
  val changeToNavigate: RefComparisonChange,
  val contentBefore: String?,
  val contentAfter: String?
) {
  fun toTaskData() =
    ReviewBuddyFileData(rawLocalPath, contentBefore, contentAfter)
}

sealed interface AIReviewResponseState

data object AIReviewRequested : AIReviewResponseState

data class AIReviewSummaryReceived(val summary: String) : AIReviewResponseState

data class AIFileReviewsPartiallyReceived(
  val summary: String,
  val reviewedFiles: List<AIFileReviewResponse>
) : AIReviewResponseState

data class AIReviewCompleted(
  val summary: String,
  val sortedFilesResponse: List<AIFileReviewResponse>
) : AIReviewResponseState

data class AIReviewFailed(
  val error: String
) : AIReviewResponseState

class AIReviewResponse(
  /**
   * The normal flow is: [AIReviewRequested] &rarr; [AIReviewSummaryReceived] &rarr; [AIReviewCompleted].
   *
   * [AIReviewFailed] might occur after each state except after [AIReviewCompleted].
   */
  val state: StateFlow<AIReviewResponseState>
)

data class AIFileReviewResponse(
  val file: String,
  val summary: String,
  val highlights: List<AIComment>,
  val comments: List<AIComment>,
)

data class AIComment(
  val lineNumber: Int,
  val reasoning: String,
  val comment: String,
)

data class GHPRAIReview(val ideaHtml: String,
                        val summaryHtml: String?,
                        val files: List<GHPRAIReviewFile> = emptyList(),
                        val reviewCompleted: Boolean)

data class GHPRAIReviewFile(val file: String,
                            val summary: String,
                            val comments: List<GHPRAIComment>,
                            val req: ReviewFileAIData
)