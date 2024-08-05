// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.util.Side
import com.intellij.ml.llm.core.grazieAPI.tasks.vcs.reviewBuddy.ReviewBuddyFileData
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vcs.FilePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.ai.comment.GHPRAIComment
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel

data class ReviewFileAiData(
  val path: FilePath,
  val rawLocalPath: String,
  val changeToNavigate: RefComparisonChange,
  val contentBefore: String?,
  val contentAfter: String?
) {
  fun toTaskData() =
    ReviewBuddyFileData(rawLocalPath, contentBefore, contentAfter)
}

sealed interface AiReviewResponseState

data object AiReviewRequested : AiReviewResponseState

data class AiReviewSummaryReceived(val summary: String) : AiReviewResponseState

data class AiFileReviewsPartiallyReceived(
  val summary: String,
  val reviewedFiles: List<AiFileReviewResponse>
) : AiReviewResponseState

data class AiReviewCompleted(
  val summary: String,
  val sortedFilesResponse: List<AiFileReviewResponse>
) : AiReviewResponseState

data class AiReviewFailed(
  val error: String
) : AiReviewResponseState

class AiReviewResponse(
  /**
   * The normal flow is: [AiReviewRequested] &rarr; [AiReviewSummaryReceived] &rarr; [AiReviewCompleted].
   *
   * [AiReviewFailed] might occur after each state except after [AiReviewCompleted].
   */
  val state: StateFlow<AiReviewResponseState>
)

data class AiFileReviewResponse(
  val file: String,
  val summary: String,
  val highlights: List<AiComment>,
  val comments: List<AiComment>,
)

data class AiComment(
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
                            val req: ReviewFileAiData
)

class GHPRAiAssistantReviewVm internal constructor(
  private val project: Project,
  private val scope: CoroutineScope,
  private val dataProvider: GHPRDataProvider
) {

  val state: MutableStateFlow<GHPRAIReview?> = dataProvider.aiReviewData.reviewState

  fun showDiffFor(change: RefComparisonChange, line: Int? = null) {
    scope.launch(Dispatchers.Main) {
      val projectVm = project.serviceAsync<GHPRToolWindowViewModel>().projectVm.value ?: return@launch
      projectVm.openPullRequestDiff(dataProvider.id, true)
      val changes = dataProvider.changesData.loadChanges().changes
      val location = line?.let { DiffLineLocation(Side.RIGHT, it) }
      val selection = ChangesSelection.Precise(changes, change, location)
      Disposer.newDisposable().use {
        projectVm.acquireDiffViewModel(dataProvider.id, it)
          .showDiffFor(selection)
      }
    }
  }

  init {
    dataProvider.aiReviewData.loadReview()
  }
}