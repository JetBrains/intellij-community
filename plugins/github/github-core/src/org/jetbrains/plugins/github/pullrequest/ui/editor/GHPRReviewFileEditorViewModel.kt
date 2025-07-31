// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.*
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.computeEmitting
import com.intellij.collaboration.util.onFailure
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewUnifiedPosition
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

interface GHPRReviewFileEditorViewModel {
  val change: RefComparisonChange

  val iconProvider: GHAvatarIconsProvider
  val currentUser: GHUser

  val originalContent: StateFlow<ComputedResult<CharSequence>?>
  val changedRanges: List<Range>

  fun getBaseContent(lines: LineRange): String?

  val canComment: Boolean
  val commentableRanges: List<Range>

  val threads: StateFlow<Collection<GHPRReviewFileEditorThreadViewModel>>
  val newComments: StateFlow<Collection<GHPRReviewFileEditorNewCommentViewModel>>

  val linesWithComments: StateFlow<Set<Int>>

  fun lookupNextComment(threadId: String): String?
  fun lookupNextComment(line: Int): String?
  fun lookupPreviousComment(threadId: String): String?
  fun lookupPreviousComment(line: Int): String?

  fun getThreadPosition(threadId: String): Pair<RefComparisonChange, Int>?
  fun requestThreadFocus(threadId: String)

  fun requestNewComment(lineIdx: Int, focus: Boolean)
  fun cancelNewComment(lineIdx: Int)

  fun showDiff(lineIdx: Int?)
}

private val LOG = logger<GHPRReviewFileEditorViewModelImpl>()

internal class GHPRReviewFileEditorViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  dataContext: GHPRDataContext,
  dataProvider: GHPRDataProvider,
  override val change: RefComparisonChange,
  private val diffData: GitTextFilePatchWithHistory,
  private val threadsVm: GHPRThreadsViewModels,
  private val allMappedThreads: StateFlow<Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData>>,
  private val showDiff: (change: RefComparisonChange, lineIdx: Int?) -> Unit,
) : GHPRReviewFileEditorViewModel {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Default)

  override val iconProvider: GHAvatarIconsProvider = dataContext.avatarIconsProvider
  override val currentUser: GHUser = dataContext.securityService.currentUser

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
    }.flowOn(Dispatchers.IO)
      .stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val changedRanges: List<Range> = diffData.patch.hunks.withoutContext().toList()

  override fun getBaseContent(lines: LineRange): String? {
    if (lines.start == lines.end) return ""
    return PatchHunkUtil.getLinesLeft(diffData.patch, lines)
  }

  override val canComment: Boolean = dataProvider.reviewData.canComment()
  override val commentableRanges: List<Range> = diffData.patch.ranges

  private val mappedThreads: StateFlow<Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData>> =
    allMappedThreads.mapState { map -> map.filterValues { it.change == change } }

  override val threads: StateFlow<Collection<GHPRReviewFileEditorThreadViewModel>> =
    threadsVm.compactThreads.mapModelsToViewModels { sharedVm ->
      MappedGHPRReviewEditorThreadViewModel(this, sharedVm, mappedThreads.mapNotNull { it[sharedVm.id] })
    }.stateInNow(cs, emptyList())

  override val linesWithComments: StateFlow<Set<Int>> =
    mappedThreads.map {
      it.values.mapNotNullTo(mutableSetOf()) { (isVisible, _, location) -> location?.takeIf { isVisible } }
    }.stateInNow(cs, emptySet())

  private val newCommentsContainer =
    MappingScopedItemsContainer.byIdentity<GHPRReviewNewCommentEditorViewModel, GHPRReviewFileEditorNewCommentViewModel>(cs) {
      GHPRReviewFileEditorNewCommentViewModelImpl(it.position.location.lineIdx, it)
    }
  override val newComments: StateFlow<Collection<GHPRReviewFileEditorNewCommentViewModel>> =
    newCommentsContainer.mappingState.mapState { it.values }

  init {
    cs.launchNow {
      threadsVm.newComments.collect { comments ->
        val commentsForChange = comments.values.filter { it.position.change == change && it.position.location.side == Side.RIGHT }
        newCommentsContainer.update(commentsForChange)
      }
    }
  }

  override fun lookupNextComment(threadId: String): String? =
    threadsVm.lookupNextComment(threadId, this::threadIsVisible)

  override fun lookupNextComment(line: Int): String? =
    threadsVm.lookupNextComment(lineToUnified(line), this::threadIsVisible)

  override fun lookupPreviousComment(threadId: String): String? =
    threadsVm.lookupPreviousComment(threadId, this::threadIsVisible)

  override fun lookupPreviousComment(line: Int): String? =
    threadsVm.lookupPreviousComment(lineToUnified(line), this::threadIsVisible)

  override fun getThreadPosition(threadId: String): Pair<RefComparisonChange, Int>? {
    val position = allMappedThreads.value[threadId] ?: return null
    return (position.change ?: return null) to (position.line ?: return null)
  }

  override fun requestThreadFocus(threadId: String) {
    threadsVm.compactThreads.value.find { it.id == threadId }?.requestFocus()
  }

  /**
   * We don't really care about the left-sided line number. It needs to be at the beginning to make sure
   * the first comment on the line is picked though.
   */
  private fun lineToUnified(line: Int): GHPRReviewUnifiedPosition =
    GHPRReviewUnifiedPosition(change, leftLine = -1, rightLine = line)

  private fun threadIsVisible(threadId: String): Boolean =
    allMappedThreads.value[threadId]?.let { it.isVisible && it.line != null && it.change?.filePathAfter != null } ?: false

  override fun requestNewComment(lineIdx: Int, focus: Boolean) {
    val position = GHPRReviewCommentPosition(change, GHPRReviewCommentLocation.SingleLine(Side.RIGHT, lineIdx))
    val sharedVm = threadsVm.requestNewComment(position)
    if (focus) {
      cs.launchNow {
        newCommentsContainer.addIfAbsent(sharedVm).requestFocus()
      }
    }
  }

  override fun cancelNewComment(lineIdx: Int) {
    val position = GHPRReviewCommentPosition(change, GHPRReviewCommentLocation.SingleLine(Side.RIGHT, lineIdx))
    threadsVm.cancelNewComment(position)
  }

  override fun showDiff(lineIdx: Int?) {
    showDiff(change, lineIdx)
  }
}

internal val TextFilePatch.ranges: List<Range>
  get() = hunks.map(PatchHunkUtil::getRange)