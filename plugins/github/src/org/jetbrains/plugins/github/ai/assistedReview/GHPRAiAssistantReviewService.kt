// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import com.intellij.ml.llm.core.chat.messages.*
import com.intellij.ml.llm.core.chat.session.ChatSession
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.platform.util.coroutines.childScope
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.github.ai.GithubAIBundle
import org.jetbrains.plugins.github.ai.assistedReview.llm.ask
import org.jetbrains.plugins.github.ai.assistedReview.llm.askRaw
import org.jetbrains.plugins.github.ai.assistedReview.llm.chatSession
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
  private val commentChatInitialContexts = ConcurrentHashMap<AiComment, AiCommentChatInitialContext>()
  private val commentChats = ConcurrentHashMap<AiComment, GHPRAICommentChat>()

  suspend fun chatAboutComment(comment: AiComment): GHPRAICommentChat {
    val initialContext = commentChatInitialContexts[comment] ?: error("Initial chat comment context not found")
    val chatSession = createCommentChatSession(initialContext)
    return commentChats.getOrPut(comment) { AiAssistantReviewCommentChat(comment, chatSession) }
  }

  fun askChatToReview(
    files: List<ReviewFileAiData>,
    runQueryPerFile: Boolean = true,
  ): AiReviewResponse {
    val state = MutableStateFlow<AiReviewResponseState>(AiReviewRequested)
    val reviewRequestScope = cs.childScope("Review requested")
    reviewRequestScope.launch {
      val reviewSummaryCompletableMessage = getReviewSummary(files)
      reviewSummaryCompletableMessage.stateFlow
        .catch { state.emit(AiReviewFailed(error = it.localizedMessage)) }
        .collect {
          when (it) {
            is ThinkingState -> Unit
            is ReadyState -> {
              val summary = reviewSummaryCompletableMessage.text.unwrap()
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
            is CancelledState -> {
              state.emit(AiReviewFailed(error = "Cancelled"))
              throw CancellationException("Underlying flow cancelled")
            }
            is ErrorState -> {
              state.emit(AiReviewFailed(error = it.text))
              throw CancellationException("Error occurred in underlying flow")
            }
            else -> error("Unexpected state: $it")
          }
        }
    }
    return AiReviewResponse(state)
  }

  private suspend fun getReviewSummary(files: List<ReviewFileAiData>): CompletableMessage {
    val chatSession = chatSession(project) {
      user(GithubAIBundle.message("review.buddy.llm.system.prompt"))
      assistant(GithubAIBundle.message("review.buddy.llm.system.response"))
    }
    return chatSession.askRaw(
      project,
      displayPrompt = GithubAIBundle.message("review.buddy.llm.summary.display.text"),
      prompt = ReviewBuddyPrompts.summarize(prepareMergeRequestData(files))
    )
  }

  private suspend fun getFileReview(summary: String, file: ReviewFileAiData): AiFileReviewResponse {
    val chatSession = chatSession(project) {
      user(GithubAIBundle.message("review.buddy.llm.system.prompt"))
      assistant(GithubAIBundle.message("review.buddy.llm.system.response"))
      user(ReviewBuddyPrompts.summaryStub(summary))
      assistant(ReviewBuddyPrompts.modelReplyToContinue())
    }
    val reviewFilePrompt = ReviewBuddyPrompts.fileReviewGuide(
      file.rawLocalPath,
      populateLineNumbers(file.contentBefore).orEmpty(),
      populateLineNumbers(file.contentAfter).orEmpty()
    )
    val response = chatSession.ask(
      project,
      displayPrompt = "Review ${file.rawLocalPath}",
      prompt = reviewFilePrompt
    )
    val jsonResponse = extractJsonFromResponse(response)
    val parsedResponse = try {
      parseFileReview(jsonResponse)
    }
    catch (e: Exception) {
      thisLogger().warn("Failed to parse JSON response: $jsonResponse", e)
      FileReviewAiResponse(summary = "Failed to parse JSON response: ${e.localizedMessage}", comments = emptyList())
    }
    val aiComments = parsedResponse.comments.map { it.toModelViewComment() }
    aiComments.forEach {
      commentChatInitialContexts[it] = AiCommentChatInitialContext(summary, reviewFilePrompt, response, it)
    }
    return AiFileReviewResponse(
      file.rawLocalPath,
      parsedResponse.summary,
      highlights = emptyList(),
      comments = aiComments
    )
  }

  private suspend fun getAllFileReviews(summary: String, files: List<ReviewFileAiData>): List<AiFileReviewResponse> {
    val chatSession = chatSession(project) {
      user(GithubAIBundle.message("review.buddy.llm.system.prompt"))
      assistant(GithubAIBundle.message("review.buddy.llm.system.response"))
      user(ReviewBuddyPrompts.summarize(prepareMergeRequestData(files)))
      assistant(summary)
    }
    val response = chatSession.ask(
      project,
      displayPrompt = "Reviewing files from the MR",
      prompt = ReviewBuddyPrompts.allFileReviewGuides()
    )
    val jsonResponse = extractJsonFromResponse(response)
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
        comments = fileResponse.comments.map { it.toModelViewComment() }
      )
    }
  }

  private fun parseFileReview(jsonResponse: String): FileReviewAiResponse =
    Json.decodeFromString<FileReviewAiResponse>(jsonResponse).sorted()

  private fun parseAllFileReviews(jsonResponse: String): List<NamedFileReviewAiResponse> =
    Json.decodeFromString<List<NamedFileReviewAiResponse>>(jsonResponse).map { it.sorted() }

  private fun ReviewCommentAiResponse.toModelViewComment(): AiComment =
    AiComment(lineNumber, reasoning, comment)

  private data class AiCommentChatInitialContext(
    val summary: String,
    val reviewFilePrompt: String,
    val reviewFileResponse: String,
    val aiComment: AiComment,
  )

  private suspend fun createCommentChatSession(initialContext: AiCommentChatInitialContext): ChatSession =
    chatSession(project) {
      user(GithubAIBundle.message("review.buddy.llm.system.prompt"))
      assistant(GithubAIBundle.message("review.buddy.llm.system.response"))
      user(ReviewBuddyPrompts.summaryStub(initialContext.summary))
      assistant(ReviewBuddyPrompts.modelReplyToContinue())
      user(initialContext.reviewFilePrompt)
      assistant(initialContext.reviewFileResponse)
    }

  fun toLocalPath(filePath: FilePath): String {
    return filePath.path.removePrefix(repository.root.path).removePrefix("/")
  }

  private inner class AiAssistantReviewCommentChat(
    private val aiComment: AiComment,
    private val chatSession: ChatSession,
  ) : GHPRAICommentChat {
    override val messages: MutableSharedFlow<GHPRAICommentChatMessage> = MutableSharedFlow(replay = Int.MAX_VALUE)

    override suspend fun sendMessage(message: String) {
      val wrappedMessage = ReviewBuddyPrompts.discussionComment(aiComment.lineNumber, message)
      processDiscussionMessage(message, wrappedMessage)
    }

    override suspend fun summarizeDiscussion() {
      val message = GithubAIBundle.message("review.buddy.llm.discussion.summarize.display")
      val wrappedMessage = ReviewBuddyPrompts.discussionSummarize()
      processDiscussionMessage(message, wrappedMessage)
    }

    private suspend fun processDiscussionMessage(message: String, wrappedMessage: String) {
      messages.emit(GHPRAICommentChatMessage(message, isResponse = false))
      val completableMessage = chatSession.askRaw(project, displayPrompt = message, prompt = wrappedMessage)
      cs.childScope("AI assistant response").launch {
        val response = completableMessage.waitForCompletion()
        messages.emit(GHPRAICommentChatMessage(response, isResponse = true))
      }
    }
  }
}