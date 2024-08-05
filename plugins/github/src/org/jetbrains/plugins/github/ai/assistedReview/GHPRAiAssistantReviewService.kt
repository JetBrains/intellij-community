// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import ai.grazie.model.llm.chat.v5.LLMChat
import ai.grazie.model.task.data.TaskStreamData
import com.intellij.ml.llm.core.grazieAPI.GrazieApiClient
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.PrivacySafeTaskCall
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddyDiscussionCommentTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddyReviewAllFilesTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddyReviewFileTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddySummarizeDiscussionTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddySummarizeTaskCallBuilder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.platform.util.coroutines.childScope
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.github.ai.GithubAIBundle
import org.jetbrains.plugins.github.ai.assistedReview.llm.waitForCompletion
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRAICommentChat
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRAICommentChatMessage
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class GHPRAiAssistantReviewService(
  private val project: Project,
  private val cs: CoroutineScope,
  private val repository: GitRepository,
) {
  private val commentChatInitialChats = ConcurrentHashMap<AiComment, LLMChat>()
  private val commentChats = ConcurrentHashMap<AiComment, GHPRAICommentChat>()

  private val grazieClient = GrazieApiClient.getClient()

  fun chatAboutComment(comment: AiComment): GHPRAICommentChat {
    val initialChat = commentChatInitialChats[comment] ?: error("Initial chat comment context not found")
    return commentChats.getOrPut(comment) { AiAssistantReviewCommentChat(comment, initialChat) }
  }

  fun askChatToReview(
    files: List<ReviewFileAiData>,
    runQueryPerFile: Boolean = true,
  ): AiReviewResponse {
    val state = MutableStateFlow<AiReviewResponseState>(AiReviewRequested)
    val reviewRequestScope = cs.childScope("Review requested")

    reviewRequestScope.launch {
      val reviewSummaryCompletableMessage = getReviewSummary(files)

      val summary: String
      try {
        summary = reviewSummaryCompletableMessage.waitForCompletion()
      } catch(_: CancellationException) {
        state.emit(AiReviewFailed(error = "Cancelled"))
        return@launch
      } catch(e: Exception) {
        state.emit(AiReviewFailed(error = e.localizedMessage))
        return@launch
      }

      state.emit(AiReviewSummaryReceived(summary = summary))
      reviewRequestScope.launch {
        val fileReviews = if (runQueryPerFile) {
          val reviewedFiles = mutableListOf<AiFileReviewResponse>()
          files.forEach { file ->
            reviewedFiles.add(getFileReview(summary, file))
            state.emit(AiFileReviewsPartiallyReceived(summary, reviewedFiles.toList()))
          }
          reviewedFiles
        }
        else {
          getAllFileReviews(summary, files)
        }
        state.emit(AiReviewCompleted(summary, fileReviews))
      }

      throw CancellationException("Summary received")
    }
    return AiReviewResponse(state)
  }

  private suspend fun getReviewSummary(files: List<ReviewFileAiData>): Flow<TaskStreamData> {
    val task = ReviewBuddySummarizeTaskCallBuilder(files.map { it.toTaskData() }).build()
    return grazieClient.sendTaskRequest(project, task) ?: error("Failed to send task")
  }

  private suspend fun getFileReview(summary: String, file: ReviewFileAiData): AiFileReviewResponse {
    val taskBuilder = ReviewBuddyReviewFileTaskCallBuilder(summary, file.toTaskData())
    val task = taskBuilder.build()

    val response = grazieClient.sendTaskRequest(project, task)?.waitForCompletion() ?: error("Failed to send task")
    val jsonResponse = extractJsonFromResponse(response)
    val parsedResponse = try {
      parseFileReview(jsonResponse)
    }
    catch (e: Exception) {
      thisLogger().warn("Failed to parse JSON response: $jsonResponse", e)
      FileReviewAiResponse(summary = "Failed to parse JSON response: ${e.localizedMessage}", comments = emptyList())
    }

    val chat = LLMChat.build {
      messages(taskBuilder.asChat())
      assistant(response)
    }
    val aiComments = parsedResponse.comments.map { it.toModelComment() }
    aiComments.forEach { commentChatInitialChats[it] = chat }

    return AiFileReviewResponse(
      file.rawLocalPath,
      parsedResponse.summary,
      highlights = emptyList(),
      comments = aiComments
    )
  }

  private suspend fun getAllFileReviews(summary: String, files: List<ReviewFileAiData>): List<AiFileReviewResponse> {
    val task = ReviewBuddyReviewAllFilesTaskCallBuilder(summary, files.map { it.toTaskData() }).build()

    val response = grazieClient.sendTaskRequest(project, task) ?: error("Failed to send task")
    val jsonResponse = extractJsonFromResponse(response.waitForCompletion())
    val parsedResponse = try {
      parseAllFileReviews(jsonResponse)
    }
    catch (e: Exception) {
      thisLogger().warn("Failed to parse JSON response: $jsonResponse", e)
      emptyList()
    }

    return parsedResponse.map { fileResponse ->
      AiFileReviewResponse(
        fileResponse.filename,
        fileResponse.summary,
        highlights = emptyList(),
        comments = fileResponse.comments.map { it.toModelComment() }
      )
    }
  }

  private fun parseFileReview(jsonResponse: String): FileReviewAiResponse =
    Json.decodeFromString<FileReviewAiResponse>(jsonResponse).sorted()

  private fun parseAllFileReviews(jsonResponse: String): List<NamedFileReviewAiResponse> =
    Json.decodeFromString<List<NamedFileReviewAiResponse>>(jsonResponse).map { it.sorted() }

  private fun ReviewCommentAiResponse.toModelComment(): AiComment =
    AiComment(lineNumber, reasoning, comment)

  fun toLocalPath(filePath: FilePath): String {
    return filePath.path.removePrefix(repository.root.path).removePrefix("/")
  }

  private inner class AiAssistantReviewCommentChat(
    private val aiComment: AiComment,
    initialChat: LLMChat,
  ) : GHPRAICommentChat {
    private var chat = initialChat
    override val messages: MutableSharedFlow<GHPRAICommentChatMessage> = MutableSharedFlow(replay = Int.MAX_VALUE)

    override suspend fun sendMessage(message: String) {
      val task = ReviewBuddyDiscussionCommentTaskCallBuilder(aiComment.lineNumber, message, chat).build()
      processDiscussionMessage(message, task)
    }

    override suspend fun summarizeDiscussion() {
      val message = GithubAIBundle.message("review.buddy.llm.discussion.summarize.display")
      val task = ReviewBuddySummarizeDiscussionTaskCallBuilder(chat).build()
      processDiscussionMessage(message, task)
    }

    private suspend fun processDiscussionMessage(message: String, task: PrivacySafeTaskCall) {
      messages.emit(GHPRAICommentChatMessage(message, isResponse = false))
      val response = grazieClient.sendTaskRequest(project, task) ?: error("Failed to send task")

      cs.childScope("AI assistant response").launch {
        val response = response.waitForCompletion()
        messages.emit(GHPRAICommentChatMessage(response, isResponse = true))
      }
    }
  }
}