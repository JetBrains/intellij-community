// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.diff

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.viewer.showCodeReview
import com.intellij.collaboration.ui.codereview.editor.CodeReviewCommentableEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorModel
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
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPREditorMappedComponentModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewEditorGutterControlsState
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel
import org.jetbrains.plugins.github.pullrequest.ui.editor.createRenderer
import org.jetbrains.plugins.github.util.GithubSettings
import kotlin.math.max
import kotlin.math.min

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
            if (changeVm == null) return@collectScoped

            val userIcon = reviewVm.iconProvider.getIcon(reviewVm.currentUser.url, 16)

            if (settings.isAutomaticallyMarkAsViewed) {
              changeVm.markViewed()
            }

            viewer.showCodeReview({ locationToLine, lineToLocations ->
                                    DiffEditorModel(this, changeVm, locationToLine, lineToLocations)
                                  }, { createRenderer(it, userIcon) })
          }
        }
      }.cancelOnDispose(viewer)
    }
  }
}

private class DiffEditorModel(
  cs: CoroutineScope,
  private val diffVm: GHPRDiffChangeViewModel,
  private val locationToLine: (DiffLineLocation) -> Int?,
  private val lineToLocation: (Int) -> DiffLineLocation?,
) : CodeReviewEditorModel<GHPREditorMappedComponentModel>,
    CodeReviewCommentableEditorModel.WithMultilineComments {

  private val threads = diffVm.threads.mapModelsToViewModels { MappedThread(cs, it) }.stateInNow(cs, emptyList())
  private val newComments = diffVm.newComments.mapModelsToViewModels { MappedNewComment(it) }.stateInNow(cs, emptyList())
  private val aiComments = diffVm.aiComments.mapModelsToViewModels { MappedAIComment(it) }.stateInNow(cs, emptyList())

  override val inlays: StateFlow<Collection<GHPREditorMappedComponentModel>> =
    combineStateIn(cs, threads, newComments, aiComments) { threads, new, ai -> threads + new + ai }

  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    diffVm.locationsWithDiscussions.map {
      val lines = it.mapNotNullTo(mutableSetOf(), locationToLine)
      GHPRReviewEditorGutterControlsState(lines, if (diffVm.canComment) transferRanges(diffVm.commentableRanges) else emptyList())
    }.stateInNow(cs, null)

  private fun transferRanges(ranges: List<Range>): List<LineRange> = ranges.mapNotNull {
    val leftRange = getSideRange(it, Side.LEFT)
    val rightRange = getSideRange(it, Side.RIGHT)
    if (leftRange != null && rightRange != null) {
      LineRange(min(leftRange.start, rightRange.start), max(leftRange.end, rightRange.end))
    }
    else leftRange ?: rightRange
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
    val end = locationToLine(side to endLineIdx.dec())?.inc() ?: return null
    return LineRange(start, end)
  }

  override fun canCreateComment(lineRange: LineRange): Boolean {
    if (lineRange.start == lineRange.end) return true
    val loc1 = lineToLocation(lineRange.start) ?: return false
    val loc2 = lineToLocation(lineRange.end) ?: return false
    return loc1.first == loc2.first
  }

  override fun requestNewComment(lineRange: LineRange) {
    val loc1 = lineToLocation(lineRange.start) ?: return
    val loc2 = lineToLocation(lineRange.end) ?: return
    if (loc1.first != loc2.first) return
    val loc = GHPRReviewCommentLocation.MultiLine(loc1.first, loc1.second, loc2.second)
    diffVm.requestNewComment(loc, true)
  }

  override fun requestNewComment(lineIdx: Int) {
    val loc = lineToLocation(lineIdx) ?: return
    diffVm.requestNewComment(GHPRReviewCommentLocation.SingleLine(loc.first, loc.second), true)
  }

  override fun toggleComments(lineIdx: Int) {
    inlays.value.asSequence().filter { it.line.value == lineIdx }.filterIsInstance<Hideable>().syncOrToggleAll()
  }

  private inner class MappedThread(parentCs: CoroutineScope, vm: GHPRReviewThreadDiffViewModel)
    : GHPREditorMappedComponentModel.Thread<GHPRCompactReviewThreadViewModel>(vm) {
    private val cs = parentCs.childScope(javaClass.name)
    override val isVisible: StateFlow<Boolean> = combineStateIn(cs, vm.isVisible, hiddenState) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.location.mapState { loc -> loc?.let { locationToLine(it) } }
  }

  private inner class MappedNewComment(vm: GHPRNewCommentDiffViewModel)
    : GHPREditorMappedComponentModel.NewComment<GHPRReviewNewCommentEditorViewModel>(vm) {
    private val location = vm.position.location.let { it.side to it.lineIdx }
    override val key: Any = "NEW_${vm.position.location}"
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val line: StateFlow<Int?> = MutableStateFlow(locationToLine(location))
  }

  private inner class MappedAIComment(vm: GHPRAICommentViewModel)
    : GHPREditorMappedComponentModel.AIComment(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible
    override val line: StateFlow<Int?> = MutableStateFlow(vm.location?.let(locationToLine))
  }
}