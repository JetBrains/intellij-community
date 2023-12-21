// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.computationStateIn
import com.intellij.collaboration.async.launchNowIn
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.*
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil.getLinesInRange
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.indeterminateStep
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.remote.hosting.infoStateIn
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChange
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChangeApplier
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.createThreadsRequestsFlow
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel.Companion.getRemoteDescriptor

private val LOG = logger<GHPRReviewCommentBodyViewModel>()

class GHPRReviewCommentBodyViewModel internal constructor(
  parentCs: CoroutineScope,
  val project: Project,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  private val threadId: String,
  private val commentId: String
) {
  private val cs = parentCs.childScope(CoroutineName("GH PR comment suggestion VM"))
  private val detailsData = dataProvider.detailsData
  private val reviewData = dataProvider.reviewData

  private val taskLauncher = SingleCoroutineLauncher(cs)

  val htmlImageLoader: AsyncHtmlImageLoader = dataContext.htmlImageLoader
  private val server: GithubServerPath = dataContext.repositoryDataService.repositoryMapping.repository.serverPath
  private val repository: GitRepository = dataContext.repositoryDataService.remoteCoordinates.repository

  private val threadData = MutableStateFlow<ThreadData?>(null)
  private val canResolvedThread = MutableStateFlow(false)
  val body: StateFlow<String>

  init {
    body = MutableStateFlow("")
    reviewData.createThreadsRequestsFlow().computationStateIn(cs).mapNotNull { it.getOrNull() }.onEach { threads ->
      val thread = threads.find { it.id == threadId }
      threadData.value = thread?.let {
        val lineCount = (if (it.line != null && it.startLine != null) {
          it.line - it.startLine
        }
        else if (it.originalLine != null && it.originalStartLine != null) {
          it.originalLine - it.originalStartLine
        }
        else {
          1
        }).coerceAtLeast(1)
        val originalEndLineIndex = it.originalLine?.dec()
        val originalStartLineIndex = it.originalStartLine?.dec() ?: originalEndLineIndex
        val endLineIndex = it.line?.dec()
        val startLineIndex = it.startLine?.dec() ?: endLineIndex
        ThreadData(it.isResolved, it.path, it.diffHunk,
                   lineCount,
                   originalStartLineIndex,
                   originalEndLineIndex,
                   startLineIndex,
                   endLineIndex)
      }
      canResolvedThread.value = thread?.viewerCanResolve ?: false

      body.value = thread?.comments?.find { it.id == commentId }?.body.orEmpty()
    }.launchNowIn(cs)
  }

  val blocks: StateFlow<List<GHPRCommentBodyBlock>> = threadData.combineState(body) { thread, body ->
    if (thread == null) return@combineState emptyList()
    val markdownConverter = GHMarkdownToHtmlConverter(project)
    val suggestions = body.getSuggestions()
    if (suggestions.isEmpty()) {
      val html = markdownConverter.convertMarkdown(body)
      return@combineState listOf(GHPRCommentBodyBlock.HTML(html))
    }
    else {
      val patchReader = PatchReader(PatchHunkUtil.createPatchFromHunk("_", thread.diffHunk))
      val hunk = patchReader.readTextPatches().firstOrNull()?.hunks?.firstOrNull() ?: run {
        LOG.warn("Empty diff hunk for thread $thread")
        val html = markdownConverter.convertMarkdown(body)
        return@combineState listOf(GHPRCommentBodyBlock.HTML(html))
      }
      val code = hunk.lines
        .filter { it.type != PatchLine.Type.REMOVE }
        .takeLast(thread.codeLinesCount)
        .joinToString(separator = "\n") { it.text }

      val htmlBody = markdownConverter.convertMarkdownWithSuggestedChange(body, thread.filePath, code)
      val content = htmlBody.removePrefix("<body>").removeSuffix("</body>")
      val blocks = GHPRReviewCommentComponentFactory.collectCommentBlocks(content)
      var suggestionIdx = 0
      blocks.map {
        when (it.commentType) {
          GHPRReviewCommentComponentFactory.CommentType.COMMENT -> {
            GHPRCommentBodyBlock.HTML(it.content)
          }
          GHPRReviewCommentComponentFactory.CommentType.SUGGESTED_CHANGE -> {
            val suggestion = suggestions.getOrNull(suggestionIdx)
            if (suggestion == null) {
              LOG.warn("Missing suggestion by index $suggestionIdx\nBody:\n$body\n\nContent:\n${it.content}")
            }
            try {
              createSuggestionBlock(it.content, thread, suggestion, hunk)
            }
            finally {
              suggestionIdx++
            }
          }
        }
      }
    }
  }

  private val loadedDetailsState = detailsData.createLoadedDetailsStateIn(cs)
  val isOnReviewBranch: StateFlow<Boolean> = repository.infoStateIn(cs)
    .combineState(loadedDetailsState) { _, details ->
      val remote = details?.getRemoteDescriptor(server) ?: return@combineState false
      GitRemoteBranchesUtil.isRemoteBranchCheckedOut(repository, remote, details.headRefName)
    }

  fun applySuggestionLocally(patch: TextFilePatch) {
    taskLauncher.launch(Dispatchers.Default) {
      try {
        withBackgroundProgress(project, GithubBundle.message("pull.request.timeline.comment.suggested.changes.progress.bar.apply")) {
          val applyStatus = indeterminateStep(GithubBundle.message("pull.request.comment.suggested.changes.applying")) {
            GHSuggestedChangeApplier.applySuggestedChange(project, repository, patch)
          }
          if (applyStatus == ApplyPatchStatus.SUCCESS && canResolvedThread.value) {
            indeterminateStep(GithubBundle.message("pull.request.comment.suggested.changes.resolving")) {
              reviewData.resolveThread(EmptyProgressIndicator(), threadId).await()
            }
          }
        }
      }
      catch (e: Exception) {
        LOG.warn("Failed to apply suggested change\n${patch.hunks.joinToString("\n\n") { it.text }}", e)
      }
    }
  }

  fun commitSuggestion(patch: TextFilePatch, commitMessage: String) {
    taskLauncher.launch(Dispatchers.Default) {
      try {
        withBackgroundProgress(project, GithubBundle.message("pull.request.timeline.comment.suggested.changes.progress.bar.commit")) {
          val applyStatus = indeterminateStep(GithubBundle.message("pull.request.comment.suggested.changes.committing")) {
            GHSuggestedChangeApplier.commitSuggestedChanges(project, repository, patch, commitMessage)
          }
          if (applyStatus == ApplyPatchStatus.SUCCESS && canResolvedThread.value) {
            indeterminateStep(GithubBundle.message("pull.request.comment.suggested.changes.resolving")) {
              reviewData.resolveThread(EmptyProgressIndicator(), threadId).await()
            }
          }
        }
      }
      catch (e: Exception) {
        LOG.warn("Failed to apply suggested change\n${patch.hunks.joinToString("\n\n") { it.text }}", e)
      }
    }
  }

  private fun createSuggestionBlock(htmlContent: String,
                                    thread: ThreadData,
                                    suggestion: List<String>?,
                                    hunk: PatchHunk): GHPRCommentBodyBlock.SuggestedChange {
    val applicability: GHPRCommentBodyBlock.SuggestionsApplicability
    val patch: TextFilePatch?
    if (thread.resolved) {
      applicability = GHPRCommentBodyBlock.SuggestionsApplicability.RESOLVED
      patch = null
    }
    else if (thread.originalStartLineIndex == null || thread.originalEndLineIndex == null ||
             thread.startLineIndex == null || thread.endLineIndex == null) {
      applicability = GHPRCommentBodyBlock.SuggestionsApplicability.OUTDATED
      patch = null
    }
    else {
      applicability = GHPRCommentBodyBlock.SuggestionsApplicability.APPLICABLE
      if (suggestion == null) {
        patch = null
      }
      else {
        val suggestionIndexShift = (suggestion.size - 1).coerceAtLeast(0)
        val suggestedChangePatchHunk = PatchHunk(thread.startLineIndex, thread.endLineIndex,
                                                 thread.startLineIndex, thread.startLineIndex + suggestionIndexShift)
        getLinesInRange(hunk, Side.RIGHT, LineRange(thread.originalStartLineIndex, thread.originalEndLineIndex + 1)).forEach { line ->
          suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.REMOVE, line.text))
        }
        suggestion.forEach {
          suggestedChangePatchHunk.addLine(PatchLine(PatchLine.Type.ADD, it))
        }
        patch = TextFilePatch(Charsets.UTF_8).apply {
          beforeName = thread.filePath
          afterName = thread.filePath
          addHunk(suggestedChangePatchHunk)
        }
      }
    }
    return GHPRCommentBodyBlock.SuggestedChange(htmlContent, patch, applicability)
  }

  private data class ThreadData(
    val resolved: Boolean,
    val filePath: String,
    val diffHunk: String,
    val codeLinesCount: Int,
    val originalStartLineIndex: Int?,
    val originalEndLineIndex: Int?,
    val startLineIndex: Int?,
    val endLineIndex: Int?
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun GHPRDetailsDataProvider.createLoadedDetailsStateIn(cs: CoroutineScope): StateFlow<GHPullRequest?> =
  callbackFlow {
    val disposable = Disposer.newDisposable()
    addDetailsLoadedListener(disposable) {
      trySend(loadDetails())
    }
    send(loadDetails())
    awaitClose { Disposer.dispose(disposable) }
  }.mapLatest {
    runCatching {
      it.await()
    }.getOrNull()
  }.stateIn(cs, SharingStarted.Eagerly, loadedDetails)

private fun String.getSuggestions(): List<List<String>> {
  val result = mutableListOf<List<String>>()
  var intermediateResult: MutableList<String>? = null
  for (line in lines()) {
    if (intermediateResult == null && line.startsWith(GHSuggestedChange.SUGGESTION_BLOCK_START)) {
      intermediateResult = mutableListOf()
      continue
    }
    else if (intermediateResult != null && line.endsWith(GHSuggestedChange.SUGGESTION_BLOCK_END)) {
      result.add(intermediateResult)
      intermediateResult = null
      continue
    }

    intermediateResult?.add(line)
  }
  return result
}

sealed interface GHPRCommentBodyBlock {
  data class HTML(val body: String) : GHPRCommentBodyBlock
  data class SuggestedChange(
    val bodyHtml: String,
    val patch: TextFilePatch?,
    val applicability: SuggestionsApplicability
  ) : GHPRCommentBodyBlock

  enum class SuggestionsApplicability {
    APPLICABLE, RESOLVED, OUTDATED
  }
}