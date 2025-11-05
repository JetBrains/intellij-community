// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.ui.codereview.diff.viewer.EditorModelFactory
import com.intellij.collaboration.ui.codereview.diff.viewer.showCodeReview
import com.intellij.collaboration.ui.codereview.editor.CodeReviewCommentableEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewNavigableEditorViewModel
import com.intellij.collaboration.util.Hideable
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.syncOrToggleAll
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHPRInlayUtils
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.lineLocation
import org.jetbrains.plugins.github.pullrequest.ui.diff.util.LineRangeUtil
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPREditorMappedComponentModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewEditorGutterControlsState
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.createRenderer
import org.jetbrains.plugins.github.util.GithubSettings

internal class GHPRReviewDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return

    if (viewer !is DiffViewerBase) return

    val diffVm = context.getUserData(GHPRDiffViewModel.KEY) ?: return
    val change = request.getUserData(RefComparisonChange.KEY) ?: return

    project.service<InlaysController>().installInlays(diffVm, change, viewer)
  }

  @Service(Service.Level.PROJECT)
  private class InlaysController(parentCs: CoroutineScope) {
    private val cs = parentCs.childScope(javaClass.name, Dispatchers.Main)

    fun installInlays(reviewVm: GHPRDiffViewModel, change: RefComparisonChange, viewer: DiffViewerBase) {
      val settings = GithubSettings.getInstance()
      cs.launchNow {
        withContext(Dispatchers.Main) {
          reviewVm.getViewModelFor(change).collectScoped { changeVm ->
            viewer.context.putUserData(GHPRDiffReviewViewModel.KEY, changeVm)
            if (changeVm == null) return@collectScoped

            val userIcon = reviewVm.iconProvider.getIcon(reviewVm.currentUser.url, 16)

            if (settings.isAutomaticallyMarkAsViewed) {
              changeVm.setViewedState(isViewed = true)
            }
            val modelFactory = createEditorModelFactory(reviewVm, changeVm, change)
            viewer.showCodeReview(modelFactory) { createRenderer(it, userIcon) }
          }
        }
      }.cancelOnDispose(viewer)
    }

    private fun createEditorModelFactory(
      reviewVm: GHPRDiffViewModel,
      changeVm: GHPRDiffReviewViewModel,
      change: RefComparisonChange,
    ): EditorModelFactory<GHPRReviewDiffEditorModel> =
      {
        editor: Editor,
        side: Side?,
        locationToLine: (DiffLineLocation) -> Int?,
        lineToLocation: (Int) -> DiffLineLocation?,
        lineToUnified: (Int) -> Pair<Int, Int>,
        ->
        val cs = this
        DiffEditorModel(this, reviewVm, changeVm, locationToLine, lineToLocation) {
          val (leftLine, rightLine) = lineToUnified(it)
          UnifiedCodeReviewItemPosition(change, leftLine, rightLine)
        }.apply {
          cs.launchNow {
            inlays
              .mapStatefulToStateful { inlayModel ->
                GHPRInlayUtils.installInlayHoverOutline(this, editor, side == null, locationToLine, inlayModel)
              }
              .collect()
          }
          GHPRInlayUtils.installInlaysDimming(cs, this@apply, locationToLine)
          editor.project?.let { project ->
            GHPRInlayUtils.installInlaysFocusTracker(cs, this@apply, project)
          }
        }
      }
  }
}

internal interface GHPRReviewDiffEditorModel : CodeReviewEditorModel<GHPREditorMappedComponentModel>,
                                               CodeReviewCommentableEditorModel.WithMultilineComments,
                                               CodeReviewNavigableEditorViewModel,
                                               CodeReviewEditorGutterControlsModel.WithMultilineComments

private class DiffEditorModel(
  cs: CoroutineScope,
  private val reviewVm: GHPRDiffViewModel,
  private val diffVm: GHPRDiffReviewViewModel,
  private val locationToLine: (DiffLineLocation) -> Int?,
  private val lineToLocation: (Int) -> DiffLineLocation?,
  @RequiresEdt private val lineToUnified: (Int) -> UnifiedCodeReviewItemPosition,
) : GHPRReviewDiffEditorModel {
  private val threads = diffVm.threads.mapStatefulToStateful { MappedThread(cs, it) }.stateInNow(cs, emptyList())
  private val newComments = diffVm.newComments.mapStatefulToStateful { MappedNewComment(it) }.stateInNow(cs, emptyList())
  private val aiComments = diffVm.aiComments.mapStatefulToStateful { MappedAIComment(it) }.stateInNow(cs, emptyList())

  override val inlays: StateFlow<Collection<GHPREditorMappedComponentModel>> =
    combineStateIn(cs, threads, newComments, aiComments) { threads, new, ai -> threads + new + ai }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val linesWithNewCommentsFlow: StateFlow<Set<Int>> =
    diffVm.newComments.flatMapLatestEach { vm ->
      vm.location.map { loc -> locationToLine(loc.lineLocation) }
    }.map { lines -> lines.filterNotNull().toSet() }
      .stateInNow(cs, emptySet())

  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    combine(diffVm.locationsWithDiscussions, linesWithNewCommentsFlow) { locationsWithDiscussions, linesWithNewComments ->
      val linesWithComments = locationsWithDiscussions.mapNotNullTo(mutableSetOf(), locationToLine)
      GHPRReviewEditorGutterControlsState(
        linesWithComments, linesWithNewComments,
        if (diffVm.canComment) transferRanges(diffVm.commentableRanges, diffVm.changedRanges) else emptyList()
      )
    }.stateInNow(cs, null)

  private fun transferRanges(ranges: List<Range>, changedRanges: List<Range>): List<LineRange> {
    val leftRange = ranges.mapNotNull {
      getSideRange(it, Side.LEFT)
    }
    val rightRange = ranges.mapNotNull {
      getSideRange(it, Side.RIGHT)
    }

    if (leftRange.isEmpty() || rightRange.isEmpty()) {
      return leftRange.takeIf { it.isNotEmpty() } ?: rightRange
    }

    val leftChangedRanges: List<LineRange> = changedRanges.mapNotNull {
      getSideRange(it, Side.LEFT)
    }
    val rightChangedRanges: List<LineRange> = changedRanges.mapNotNull {
      getSideRange(it, Side.RIGHT)
    }

    return LineRangeUtil.extract(leftRange, rightRange, leftChangedRanges, rightChangedRanges)
  }

  private fun getSideRange(range: Range, side: Side): LineRange? {
    val startLineIdx = when (side) {
      Side.LEFT -> range.start1
      Side.RIGHT -> range.start2
    }
    val start = locationToLine(side to startLineIdx) ?: return null
    val endLineIdx = when (side) {
      Side.LEFT -> range.end1
      Side.RIGHT -> range.end2
    }
    val end =
      if (startLineIdx == endLineIdx) start
      else locationToLine(side to endLineIdx.dec())?.inc() ?: return null
    return LineRange(start, end)
  }

  override fun canCreateComment(lineRange: LineRange): Boolean {
    if (lineRange.start == lineRange.end) return true
    val loc1 = lineToLocation(lineRange.start) ?: return false
    val loc2 = lineToLocation(lineRange.end) ?: return false
    val gutterControls = gutterControlsState.value ?: return false
    return loc1.first == loc2.first &&
           (lineRange.start..lineRange.end).all { gutterControls.isLineCommentable(it) }
  }

  override fun requestNewComment(lineRange: LineRange) {
    val loc1 = lineToLocation(lineRange.start) ?: return
    val loc2 = lineToLocation(lineRange.end) ?: return
    if (loc1.first != loc2.first) return
    val loc = GHPRReviewCommentLocation.MultiLine(loc1.first, loc1.second, loc2.second)
    diffVm.requestNewComment(loc, true)
  }

  override fun updateCommentLines(oldLineRange: LineRange, newLineRange: LineRange) {
    diffVm.updateCommentLines(LineRange(oldLineRange.start, oldLineRange.end), LineRange(newLineRange.start, newLineRange.end))
  }

  override fun requestNewComment(lineIdx: Int) {
    val loc = lineToLocation(lineIdx) ?: return
    diffVm.requestNewComment(GHPRReviewCommentLocation.SingleLine(loc.first, loc.second), true)
  }

  override fun cancelNewComment(lineIdx: Int) {
    val loc = lineToLocation(lineIdx) ?: return
    diffVm.cancelNewComment(loc.first, loc.second)
  }

  override fun toggleComments(lineIdx: Int) {
    inlays.value.asSequence().filter { it.line.value == lineIdx }.filterIsInstance<Hideable>().syncOrToggleAll()
  }

  override val canNavigate: Boolean get() = diffVm.canNavigate

  @RequiresEdt
  override fun canGotoNextComment(threadId: String): Boolean = reviewVm.nextComment(threadId) != null

  @RequiresEdt
  override fun canGotoNextComment(line: Int): Boolean = reviewVm.nextComment(lineToUnified(line)) != null

  @RequiresEdt
  override fun canGotoPreviousComment(threadId: String): Boolean = reviewVm.previousComment(threadId) != null

  @RequiresEdt
  override fun canGotoPreviousComment(line: Int): Boolean = reviewVm.previousComment(lineToUnified(line)) != null

  @RequiresEdt
  override fun gotoNextComment(threadId: String) {
    val commentId = reviewVm.nextComment(threadId) ?: return
    reviewVm.showDiffAtComment(commentId)
  }

  @RequiresEdt
  override fun gotoNextComment(line: Int) {
    val commentId = reviewVm.nextComment(lineToUnified(line)) ?: return
    reviewVm.showDiffAtComment(commentId)
  }

  @RequiresEdt
  override fun gotoPreviousComment(threadId: String) {
    val commentId = reviewVm.previousComment(threadId) ?: return
    reviewVm.showDiffAtComment(commentId)
  }

  @RequiresEdt
  override fun gotoPreviousComment(line: Int) {
    val commentId = reviewVm.previousComment(lineToUnified(line)) ?: return
    reviewVm.showDiffAtComment(commentId)
  }

  private inner class MappedThread(parentCs: CoroutineScope, vm: GHPRReviewThreadDiffViewModel)
    : GHPREditorMappedComponentModel.Thread<GHPRCompactReviewThreadViewModel>(vm) {
    private val cs = parentCs.childScope(javaClass.name)
    override val isVisible: StateFlow<Boolean> = combineStateIn(cs, vm.mapping, hiddenState) { mapping, hidden -> mapping.isVisible && !hidden }
    override val range: StateFlow<Pair<Side, IntRange>?> = vm.mapping.mapState { mapping -> mapping.commentRange }
    override val line: StateFlow<Int?> = vm.mapping.mapState { mapping -> mapping.location?.let { locationToLine(it) } }
  }

  private inner class MappedNewComment(vm: GHPRNewCommentDiffViewModel)
    : GHPREditorMappedComponentModel.NewComment<GHPRReviewNewCommentEditorViewModel>(vm) {
    private val location: StateFlow<GHPRReviewCommentLocation> = vm.position.mapState { it.location }
    override val key: Any = "NEW_${vm.position.value.location}"
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val line: StateFlow<Int?> = location.mapState { locationToLine(it.lineLocation) }
    private val _range = MutableStateFlow<Pair<Side, IntRange>?>(when (val loc = location.value) {
                                                                   is GHPRReviewCommentLocation.SingleLine -> {
                                                                     loc.side to loc.lineIdx..loc.lineIdx
                                                                   }
                                                                   is GHPRReviewCommentLocation.MultiLine -> {
                                                                     loc.side to loc.startLineIdx..loc.lineIdx
                                                                   }
                                                                 })
    override val range: StateFlow<Pair<Side, IntRange>?> = _range.asStateFlow()
    override fun setRange(range: Pair<Side, IntRange>?) {
      _range.value = range
    }
  }

  private inner class MappedAIComment(vm: GHPRAICommentViewModel)
    : GHPREditorMappedComponentModel.AIComment(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible
    override val line: StateFlow<Int?> = MutableStateFlow(vm.location?.let(locationToLine))
    override val range: StateFlow<Pair<Side, IntRange>?> = stateFlowOf(null)
  }
}