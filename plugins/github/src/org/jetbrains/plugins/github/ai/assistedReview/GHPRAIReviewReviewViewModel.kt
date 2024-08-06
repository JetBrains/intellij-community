// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview

import ai.grazie.model.llm.chat.v5.LLMChat
import ai.grazie.model.task.data.TaskStreamData
import com.intellij.collaboration.async.mapDataToModel
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.diff.util.Side
import com.intellij.ml.llm.core.grazieAPI.GrazieApiClient
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.PrivacySafeTaskCall
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddyDiscussionCommentTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddyReviewAllFilesTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddyReviewFileTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddySummarizeDiscussionTaskCallBuilder
import com.intellij.ml.llm.grazieAPIAdapters.tasksFacade.ReviewBuddySummarizeTaskCallBuilder
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.createVcsChange
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.github.ai.GithubAIBundle
import org.jetbrains.plugins.github.ai.assistedReview.llm.waitForCompletion
import org.jetbrains.plugins.github.ai.assistedReview.model.AIComment
import org.jetbrains.plugins.github.ai.assistedReview.model.AIFileReviewResponse
import org.jetbrains.plugins.github.ai.assistedReview.model.AIFileReviewsPartiallyReceived
import org.jetbrains.plugins.github.ai.assistedReview.model.AIReviewCompleted
import org.jetbrains.plugins.github.ai.assistedReview.model.AIReviewFailed
import org.jetbrains.plugins.github.ai.assistedReview.model.AIReviewRequested
import org.jetbrains.plugins.github.ai.assistedReview.model.AIReviewResponse
import org.jetbrains.plugins.github.ai.assistedReview.model.AIReviewResponseState
import org.jetbrains.plugins.github.ai.assistedReview.model.AIReviewSummaryReceived
import org.jetbrains.plugins.github.ai.assistedReview.model.GHPRAIComment
import org.jetbrains.plugins.github.ai.assistedReview.model.GHPRAICommentPosition
import org.jetbrains.plugins.github.ai.assistedReview.model.GHPRAIReview
import org.jetbrains.plugins.github.ai.assistedReview.model.GHPRAIReviewFile
import org.jetbrains.plugins.github.ai.assistedReview.model.ReviewFileAIData
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.coroutines.cancellation.CancellationException

/**
 * Represents the results of an AI-reviewed PR.
 */
class GHPRAiAssistantReviewViewModel internal constructor(
  private val project: Project,
  private val cs: CoroutineScope,
  private val dataProvider: GHPRDataProvider,
  private val gitRepository: GitRepository,
) {
  private val prId = dataProvider.id
  private val changesData = dataProvider.changesData

  val state: MutableStateFlow<GHPRAIReview?> = MutableStateFlow(null)

  private val commentChatInitialChats = ConcurrentHashMap<AIComment, LLMChat>()
  private val commentChats = ConcurrentHashMap<AIComment, GHPRAIReviewCommentChatViewModel>()

  private val grazieClient = GrazieApiClient.getClient()

  init {
    loadReview()
  }

  fun showDiffFor(change: RefComparisonChange, line: Int? = null) {
    cs.launch(Dispatchers.Main) {
      val projectVm = project.serviceAsync<GHPRToolWindowViewModel>().projectVm.value ?: return@launch
      projectVm.openPullRequestDiff(prId, true)
      val changes = dataProvider.changesData.loadChanges().changes
      val location = line?.let { DiffLineLocation(Side.RIGHT, it) }
      val selection = ChangesSelection.Precise(changes, change, location)

      val disposable = nestedDisposable()
      projectVm.acquireDiffViewModel(prId, disposable).showDiffFor(selection)
    }
  }

  fun startThreadOnComment(comment: AIComment): GHPRAIReviewCommentChatViewModel {
    val initialChat = commentChatInitialChats[comment] ?: error("Initial chat comment context not found")
    return commentChats.getOrPut(comment) { AiAssistantReviewCommentChat(comment, initialChat) }
  }

  private fun startReview(
    files: List<ReviewFileAIData>,
    runQueryPerFile: Boolean = true,
  ): AIReviewResponse {
    val state = MutableStateFlow<AIReviewResponseState>(AIReviewRequested)
    val reviewRequestScope = cs.childScope("Review requested")

    reviewRequestScope.launch {
      val reviewSummaryCompletableMessage = getReviewSummary(files)

      val summary: String
      try {
        summary = reviewSummaryCompletableMessage.waitForCompletion()
      }
      catch (_: CancellationException) {
        state.emit(AIReviewFailed(error = "Cancelled"))
        return@launch
      }
      catch (e: Exception) {
        state.emit(AIReviewFailed(error = e.localizedMessage))
        return@launch
      }

      state.emit(AIReviewSummaryReceived(summary = summary))
      reviewRequestScope.launch {
        val fileReviews = if (runQueryPerFile) {
          val reviewedFiles = mutableListOf<AIFileReviewResponse>()
          files.forEach { file ->
            reviewedFiles.add(getFileReview(summary, file))
            state.emit(AIFileReviewsPartiallyReceived(summary, reviewedFiles.toList()))
          }
          reviewedFiles
        }
        else {
          getAllFileReviews(summary, files)
        }
        state.emit(AIReviewCompleted(summary, fileReviews))
      }

      throw CancellationException("Summary received")
    }

    return AIReviewResponse(state)
  }

  private suspend fun getReviewSummary(files: List<ReviewFileAIData>): Flow<TaskStreamData> {
    val task = ReviewBuddySummarizeTaskCallBuilder(files.map { it.toTaskData() }).build()
    return grazieClient.sendTaskRequest(project, task) ?: error("Failed to send task")
  }

  private suspend fun getFileReview(summary: String, file: ReviewFileAIData): AIFileReviewResponse {
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

    return AIFileReviewResponse(
      file.rawLocalPath,
      parsedResponse.summary,
      highlights = emptyList(),
      comments = aiComments
    )
  }

  private suspend fun getAllFileReviews(summary: String, files: List<ReviewFileAIData>): List<AIFileReviewResponse> {
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
      AIFileReviewResponse(
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

  private fun ReviewCommentAiResponse.toModelComment(): AIComment =
    AIComment(lineNumber, reasoning, comment)

  val comments: StateFlow<Collection<GHPRAIComment>?> =
    state.map {
      it?.files?.map { it.comments }?.flatten().orEmpty()
    }.stateInNow(cs, emptyList())

  fun getAICommentsForDiff(change: RefComparisonChange, diffData: GitTextFilePatchWithHistory): Flow<List<GHPRAIReviewCommentDiffViewModel>> =
    state
      .map { st -> st?.files?.find { it.req.rawLocalPath == change.filePath.let(::toLocalPath) }?.comments.orEmpty() }
      .mapDataToModel({ it.id }, { createAiComment(it, change, diffData) }, { update(it) })

  private fun CoroutineScope.createAiComment(comment: GHPRAIComment, change: RefComparisonChange, diffData: GitTextFilePatchWithHistory): GHPRAIReviewCommentDiffViewModel =
    GHPRAIReviewCommentDiffViewModel(project, this@createAiComment, this@GHPRAiAssistantReviewViewModel, comment, change, diffData)

  fun toLocalPath(filePath: FilePath): String =
    filePath.path.removePrefix(gitRepository.root.path).removePrefix("/")

  private fun loadReview() {
    cs.launch {
      changesData.changesNeedReloadSignal.withInitial(Unit).collectLatest {
        val changes = changesData.loadChanges().changes
        changesData.ensureAllRevisionsFetched()
        val files = changes.mapNotNull {
          val filePath = it.filePathAfter ?: return@mapNotNull null
          val localFilePath = toLocalPath(filePath)
          val (before, after) = withContext(Dispatchers.IO) {
            coroutineToIndicator {
              val change = it.createVcsChange(project)
              change.beforeRevision?.content to change.afterRevision?.content
            }
          }
          ReviewFileAIData(filePath, localFilePath, it, before, after)
        }

        val response = startReview(files)
        response.state.collect {
          when (it) {
            AIReviewRequested -> Unit
            is AIReviewSummaryReceived -> {
              val (idea, sum) = splitSummary(it.summary)
              state.value = GHPRAIReview(idea.convertToHtml(project), sum?.convertToHtml(project), reviewCompleted = false)
            }
            is AIFileReviewsPartiallyReceived -> {
              val (idea, sum) = splitSummary(it.summary)
              val filesRes = buildViewModel(it.reviewedFiles, files)
              state.value = GHPRAIReview(idea.convertToHtml(project), sum?.convertToHtml(project), filesRes, reviewCompleted = false)
            }
            is AIReviewCompleted -> {
              val (idea, sum) = splitSummary(it.summary)
              val sortedFilesResponse = it.sortedFilesResponse
              val filesRes = buildViewModel(sortedFilesResponse, files)
              state.value = GHPRAIReview(idea.convertToHtml(project), sum?.convertToHtml(project), filesRes, reviewCompleted = true)
            }
            is AIReviewFailed -> Unit
          }
        }
      }
    }
  }

  /**
   * Store already created [GHPRAIReviewFile] between [buildViewModel] calls.
   */
  private val alreadyBuiltFileReviews = ConcurrentHashMap<AIFileReviewResponse, GHPRAIReviewFile>()

  private fun buildViewModel(
    fileReviewResponse: List<AIFileReviewResponse>,
    files: List<ReviewFileAIData>,
  ): List<GHPRAIReviewFile> {
    val filesRes = fileReviewResponse.mapNotNull { fileReview ->
      val req = files.find { it.rawLocalPath == fileReview.file } ?: return@mapNotNull null
      alreadyBuiltFileReviews.getOrPut(fileReview) { buildReviewFile(fileReview, req) }
    }
    return filesRes
  }

  private fun buildReviewFile(fileReview: AIFileReviewResponse, req: ReviewFileAIData): GHPRAIReviewFile {
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

  private inner class AiAssistantReviewCommentChat(
    private val aiComment: AIComment,
    initialChat: LLMChat,
  ) : GHPRAIReviewCommentChatViewModel {
    private var chat = initialChat
    override val messages: MutableSharedFlow<GHPRAIReviewCommentChatMessage> = MutableSharedFlow(replay = Int.MAX_VALUE)

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
      messages.emit(GHPRAIReviewCommentChatMessage(message, isResponse = false))
      val response = grazieClient.sendTaskRequest(project, task) ?: error("Failed to send task")

      cs.childScope("AI assistant response").launch {
        val response = response.waitForCompletion()
        messages.emit(GHPRAIReviewCommentChatMessage(response, isResponse = true))
      }
    }
  }
}