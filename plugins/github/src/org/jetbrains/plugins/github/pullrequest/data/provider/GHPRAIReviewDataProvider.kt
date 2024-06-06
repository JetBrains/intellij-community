// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.withInitial
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.createVcsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.ai.assistedReview.*
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.ai.comment.GHPRAIComment
import org.jetbrains.plugins.github.pullrequest.data.ai.comment.GHPRAICommentPosition
import java.util.concurrent.ConcurrentHashMap

class GHPRAIReviewDataProvider(
  private val project: Project,
  parentCs: CoroutineScope,
  private val service: GHPRAiAssistantReviewService,
  private val changesData: GHPRChangesDataProvider
) {
  private val cs = parentCs.childScope(javaClass.name)

  val reviewState: MutableStateFlow<GHPRAIReview?> = MutableStateFlow(null)

  val comments: StateFlow<Collection<GHPRAIComment>?> =
    reviewState.map {
      it?.files?.map { it.comments }?.flatten().orEmpty()
    }.stateInNow(cs, emptyList())

  suspend fun discardComment(comment: GHPRAIComment) {
    comment.accepted.value = false
    comment.rejected.value = true
  }

  suspend fun acceptComment(comment: GHPRAIComment) {
    comment.accepted.value = true
    comment.rejected.value = false
  }

  suspend fun getChat(comment: GHPRAIComment): GHPRAICommentChat {
    val aiComment = comment.id as AiComment
    return service.chatAboutComment(aiComment)
  }

  fun loadReview() {
    cs.launch {
      changesData.changesNeedReloadSignal.withInitial(Unit).collectLatest {
        val changes = changesData.loadChanges().changes
        changesData.ensureAllRevisionsFetched()
        val files = changes.mapNotNull {
          val filePath = it.filePathAfter ?: return@mapNotNull null
          val localFilePath = service.toLocalPath(filePath)
          val (before, after) = withContext(Dispatchers.IO) {
            coroutineToIndicator {
              val change = it.createVcsChange(project)
              change.beforeRevision?.content to change.afterRevision?.content
            }
          }
          ReviewFileAiData(filePath, localFilePath, it, before, after)
        }

        val response = service.askChatToReview(files)
        response.state.collect {
          when (it) {
            AiReviewRequested -> Unit
            is AiReviewSummaryReceived -> {
              val (idea, sum) = splitSummary(it.summary)
              reviewState.value = GHPRAIReview(idea.convertToHtml(project), sum?.convertToHtml(project), reviewCompleted = false)
            }
            is AiFileReviewsPartiallyReceived -> {
              val (idea, sum) = splitSummary(it.summary)
              val filesRes = buildViewModel(it.reviewedFiles, files)
              reviewState.value = GHPRAIReview(idea.convertToHtml(project), sum?.convertToHtml(project), filesRes, reviewCompleted = false)
            }
            is AiReviewCompleted -> {
              val (idea, sum) = splitSummary(it.summary)
              val sortedFilesResponse = it.sortedFilesResponse
              val filesRes = buildViewModel(sortedFilesResponse, files)
              reviewState.value = GHPRAIReview(idea.convertToHtml(project), sum?.convertToHtml(project), filesRes, reviewCompleted = true)
            }
            is AiReviewFailed -> Unit
          }
        }
      }
    }
  }

  /**
   * Store already created [GHPRAIReviewFile] between [buildViewModel] calls.
   */
  private val alreadyBuiltFileReviews = ConcurrentHashMap<AiFileReviewResponse, GHPRAIReviewFile>()

  private fun buildViewModel(fileReviewResponse: List<AiFileReviewResponse>,
                             files: List<ReviewFileAiData>): List<GHPRAIReviewFile> {
    val filesRes = fileReviewResponse.mapNotNull { fileReview ->
      val req = files.find { it.rawLocalPath == fileReview.file } ?: return@mapNotNull null
      alreadyBuiltFileReviews.getOrPut(fileReview) { buildReviewFile(fileReview, req) }
    }
    return filesRes
  }

  private fun buildReviewFile(fileReview: AiFileReviewResponse, req: ReviewFileAiData): GHPRAIReviewFile {
    val comments = fileReview.comments.map { comment ->
      GHPRAIComment(
        comment,
        GHPRAICommentPosition(fileReview.file, comment.lineNumber - 1),
        comment.comment.convertToHtml(project),
        comment.reasoning.convertToHtml(project),
        comment.comment,
        comment.reasoning
      )
    }
    return GHPRAIReviewFile(fileReview.file, fileReview.summary, comments, req)
  }

  private fun splitSummary(summary: String): Pair<String, String?> =
    summary.removePrefix("## Pull Request Summary").split("## Main idea").let {
      if (it.size < 2) return it.first().trim() to null else it[1].trim() to it.first().trim()
    }
}
