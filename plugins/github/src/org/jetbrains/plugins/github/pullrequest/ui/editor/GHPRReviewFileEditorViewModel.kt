// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.util.*
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.diff.impl.patch.withoutContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.createVcsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.pullrequest.isVisible
import org.jetbrains.plugins.github.api.data.pullrequest.mapToLocation
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.threadsComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels

interface GHPRReviewFileEditorViewModel {
  val originalContent: StateFlow<ComputedResult<CharSequence>?>
  val changedRanges: List<Range>

  fun getBaseContent(lines: LineRange): String?

  val canComment: Boolean
  val commentableRanges: List<Range>

  val threads: StateFlow<Collection<GHPRReviewFileEditorThreadViewModel>>
  val newComments: StateFlow<Collection<GHPRReviewFileEditorNewCommentViewModel>>

  val linesWithComments: StateFlow<Set<Int>>

  fun requestNewComment(lineIdx: Int, focus: Boolean)
  fun cancelNewComment(lineIdx: Int)

  fun showDiff(lineIdx: Int?)
}

private val LOG = logger<GHPRReviewFileEditorViewModelImpl>()

internal class GHPRReviewFileEditorViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  dataProvider: GHPRDataProvider,
  private val change: RefComparisonChange,
  private val diffData: GitTextFilePatchWithHistory,
  private val threadsVms: GHPRThreadsViewModels,
  private val discussionsViewOption: StateFlow<DiscussionsViewOption>,
  private val showDiff: (change: RefComparisonChange, lineIdx: Int?) -> Unit
) : GHPRReviewFileEditorViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  override val originalContent: StateFlow<ComputedResult<CharSequence>?> =
    flow {
      computeEmitting {
        coroutineToIndicator {
          change.createVcsChange(project).afterRevision?.content ?: ""
        }.let {
          StringUtil.convertLineSeparators(it)
        }
      }?.onFailure {
        LOG.warn("Couldn't load head content for $change", it)
      }
    }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val changedRanges: List<Range> = diffData.patch.hunks.withoutContext().toList()

  override fun getBaseContent(lines: LineRange): String? {
    if (lines.start == lines.end) return ""
    return PatchHunkUtil.getLinesLeft(diffData.patch, lines)
  }

  override val canComment: Boolean = dataProvider.reviewData.canComment()
  override val commentableRanges: List<Range> = diffData.patch.ranges

  private val mappedThreads: StateFlow<Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData>> =
    dataProvider.reviewData.threadsComputationFlow
      .transformConsecutiveSuccesses(false) {
        combine(discussionsViewOption) { threads, viewOption ->
          threads.associateBy(GHPullRequestReviewThread::id) { threadData ->
            val isVisible = threadData.isVisible(viewOption)
            val line = threadData.mapToLocation(diffData, Side.RIGHT)?.takeIf { (side, _) -> side == Side.RIGHT }?.second
            MappedGHPRReviewEditorThreadViewModel.MappingData(isVisible, line)
          }
        }
      }.map { it.getOrNull().orEmpty() }.stateInNow(cs, emptyMap())

  override val threads: StateFlow<Collection<GHPRReviewFileEditorThreadViewModel>> =
    threadsVms.compactThreads.mapModelsToViewModels { sharedVm ->
      MappedGHPRReviewEditorThreadViewModel(this, sharedVm, mappedThreads.mapNotNull { it[sharedVm.id] })
    }.stateInNow(cs, emptyList())

  override val linesWithComments: StateFlow<Set<Int>> =
    mappedThreads.map {
      it.values.mapNotNullTo(mutableSetOf()) { (isVisible, location) -> location?.takeIf { isVisible } }
    }.stateInNow(cs, emptySet())

  private val newCommentsContainer =
    MappingScopedItemsContainer.byIdentity<GHPRReviewNewCommentEditorViewModel, GHPRReviewFileEditorNewCommentViewModel>(cs) {
      GHPRReviewFileEditorNewCommentViewModelImpl(it.position.location.lineIdx, it)
    }
  override val newComments: StateFlow<Collection<GHPRReviewFileEditorNewCommentViewModel>> =
    newCommentsContainer.mappingState.mapState { it.values }

  init {
    cs.launchNow {
      threadsVms.newComments.collect { comments ->
        val commentsForChange = comments.values.filter { it.position.change == change && it.position.location.side == Side.RIGHT }
        newCommentsContainer.update(commentsForChange)
      }
    }
  }

  override fun requestNewComment(lineIdx: Int, focus: Boolean) {
    val position = GHPRReviewCommentPosition(change, GHPRReviewCommentLocation.SingleLine(Side.RIGHT, lineIdx))
    val sharedVm = threadsVms.requestNewComment(position)
    if (focus) {
      cs.launchNow {
        newCommentsContainer.addIfAbsent(sharedVm).requestFocus()
      }
    }
  }

  override fun cancelNewComment(lineIdx: Int) {
    val position = GHPRReviewCommentPosition(change, GHPRReviewCommentLocation.SingleLine(Side.RIGHT, lineIdx))
    threadsVms.cancelNewComment(position)
  }

  override fun showDiff(lineIdx: Int?) {
    showDiff(change, lineIdx)
  }
}

internal val TextFilePatch.ranges: List<Range>
  get() = hunks.map(PatchHunkUtil::getRange)