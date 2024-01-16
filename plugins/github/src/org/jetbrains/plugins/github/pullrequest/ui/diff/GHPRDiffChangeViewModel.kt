// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.createThreadsRequestsFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition

interface GHPRDiffChangeViewModel {
  val commentableRanges: List<Range>
  val canComment: Boolean

  val threads: StateFlow<Collection<GHPRReviewThreadDiffViewModel>>
  val newComments: StateFlow<Collection<GHPRNewCommentDiffViewModel>>

  val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>>

  fun requestNewComment(location: GHPRReviewCommentLocation, focus: Boolean)
  fun cancelNewComment(location: GHPRReviewCommentLocation)

  fun markViewed()
}

internal class UpdateableGHPRDiffChangeViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
  private val dataContext: GHPRDataContext,
  private val dataProvider: GHPRDataProvider,
  private val change: RefComparisonChange,
  private val diffData: GitTextFilePatchWithHistory,
  private val discussionsViewOption: StateFlow<DiscussionsViewOption>
) : GHPRDiffChangeViewModel {
  private val cs = parentCs.childScope(classAsCoroutineName())
  private val diffDataState = MutableStateFlow(diffData)

  override val commentableRanges: List<Range> = diffData.patch.ranges
  override val canComment: Boolean = dataProvider.reviewData.canComment()

  private val mappedThreads: StateFlow<Collection<UpdateableGHPRReviewThreadDiffViewModel.MappedThreadData>> =
    dataProvider.reviewData.createThreadsRequestsFlow()
      .computationStateIn(cs)
      .transformConsecutiveSuccesses(false) {
        combine(this, diffDataState, discussionsViewOption) { threads, diffData, viewOption ->
          threads.map { threadData ->
            val isVisible = when (viewOption) {
              DiscussionsViewOption.ALL -> true
              DiscussionsViewOption.UNRESOLVED_ONLY -> !threadData.isResolved
              DiscussionsViewOption.DONT_SHOW -> false
            }
            val location = mapThread(diffData, threadData)
            UpdateableGHPRReviewThreadDiffViewModel.MappedThreadData(threadData, isVisible, location)
          }
        }
      }.map { it.getOrNull().orEmpty() }.stateInNow(cs, emptyList())

  override val threads: StateFlow<Collection<GHPRReviewThreadDiffViewModel>> =
    mappedThreads.mapDataToModel({ it.data.id }, { createThread(it) }, { update(it) }).stateInNow(cs, emptyList())
  override val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>> =
    mappedThreads.map {
      it.asSequence().filter { it.isVisible && it.location != null }.mapNotNull { it.location }.toSet()
    }.stateInNow(cs, emptySet())

  private val _newComments =
    MutableStateFlow<Map<GHPRReviewCommentLocation, GHPRNewCommentDiffViewModelImpl>>(emptyMap())
  override val newComments: StateFlow<Collection<GHPRNewCommentDiffViewModel>> =
    _newComments.mapState { it.values }

  override fun requestNewComment(location: GHPRReviewCommentLocation, focus: Boolean) {
    if (!canComment) return
    _newComments.updateAndGet { currentNewComments ->
      if (!currentNewComments.containsKey(location)) {
        val vm = createNewCommentVm(location)
        currentNewComments + (location to vm)
      }
      else {
        currentNewComments
      }
    }.apply {
      if (focus) {
        get(location)?.requestFocus()
      }
    }
  }

  override fun cancelNewComment(location: GHPRReviewCommentLocation) {
    _newComments.update {
      val oldVm = it[location]
      val newMap = it - location
      cs.launch {
        oldVm?.destroy()
      }
      newMap
    }
  }

  private fun createNewCommentVm(location: GHPRReviewCommentLocation): GHPRNewCommentDiffViewModelImpl =
    GHPRNewCommentDiffViewModelImpl(project, cs, dataContext, dataProvider,
                                    GHPRReviewCommentPosition(change, diffDataState.value.isCumulative, location)) {
      cancelNewComment(location)
    }

  override fun markViewed() {
    if (!diffData.isCumulative) return
    val repository = dataContext.repositoryDataService.repositoryMapping.gitRepository
    val repositoryRelativePath = VcsFileUtil.relativePath(repository.root, change.filePath)

    dataProvider.viewedStateData.updateViewedState(repositoryRelativePath, true)
  }

  fun updateDiffData(diffData: GitTextFilePatchWithHistory) {
    diffDataState.value = diffData
  }

  private fun CoroutineScope.createThread(data: UpdateableGHPRReviewThreadDiffViewModel.MappedThreadData): UpdateableGHPRReviewThreadDiffViewModel =
    UpdateableGHPRReviewThreadDiffViewModel(project, this, dataContext, dataProvider, data)
}

private fun mapThread(diffData: GitTextFilePatchWithHistory, threadData: GHPullRequestReviewThread): DiffLineLocation? {
  if (threadData.line == null && threadData.originalLine == null) return null

  return if (threadData.line != null) {
    val commitSha = threadData.commit?.oid ?: return null
    if (!diffData.contains(commitSha, threadData.path)) return null
    when (threadData.side) {
      Side.RIGHT -> {
        diffData.mapLine(commitSha, threadData.line - 1, Side.RIGHT)
      }
      Side.LEFT -> {
        diffData.fileHistory.findStartCommit()?.let { baseSha ->
          diffData.mapLine(baseSha, threadData.line - 1, Side.LEFT)
        }
      }
    }
  }
  else if (threadData.originalLine != null) {
    val originalCommitSha = threadData.originalCommit?.oid ?: return null
    if (!diffData.contains(originalCommitSha, threadData.path)) return null
    when (threadData.side) {
      Side.RIGHT -> {
        diffData.mapLine(originalCommitSha, threadData.originalLine - 1, Side.RIGHT)
      }
      Side.LEFT -> {
        diffData.fileHistory.findFirstParent(originalCommitSha)?.let { parentSha ->
          diffData.mapLine(parentSha, threadData.originalLine - 1, Side.LEFT)
        }
      }
    }
  }
  else {
    null
  }
}

private val TextFilePatch.ranges: List<Range>
  get() = hunks.map(PatchHunkUtil::getRange)