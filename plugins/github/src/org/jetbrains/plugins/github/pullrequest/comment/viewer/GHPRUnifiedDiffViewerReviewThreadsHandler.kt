// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewThreadMapping
import org.jetbrains.plugins.github.pullrequest.comment.ui.*
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import kotlin.math.max
import kotlin.math.min

class GHPRUnifiedDiffViewerReviewThreadsHandler(reviewProcessModel: GHPRReviewProcessModel,
                                                commentableRangesModel: SingleValueModel<List<Range>?>,
                                                reviewThreadsModel: SingleValueModel<List<GHPRDiffReviewThreadMapping>?>,
                                                viewer: UnifiedDiffViewer,
                                                componentsFactory: GHPRDiffEditorReviewComponentsFactory)
  : GHPRDiffViewerBaseReviewThreadsHandler<UnifiedDiffViewer>(commentableRangesModel, reviewThreadsModel, viewer) {

  private val commentableRanges = SingleValueModel<List<LineRange>>(emptyList())
  private val editorThreads = GHPREditorReviewThreadsModel()

  override val viewerReady: Boolean
    get() = viewer.isContentGood

  init {
    val inlaysManager = EditorComponentInlaysManager(viewer.editor as EditorImpl)

    val gutterIconRendererFactory = GHPRDiffEditorGutterIconRendererFactoryImpl(reviewProcessModel, inlaysManager,
                                                                                componentsFactory) { fileLine ->
      val (indices, side) = viewer.transferLineFromOneside(fileLine)
      val line = side.select(indices).takeIf { it >= 0 } ?: return@GHPRDiffEditorGutterIconRendererFactoryImpl null

      side to line
    }

    GHPREditorCommentableRangesController(commentableRanges, gutterIconRendererFactory, viewer.editor)
    GHPREditorReviewThreadsController(editorThreads, componentsFactory, inlaysManager)
  }

  override fun markCommentableRanges(ranges: List<Range>?) {
    if (ranges == null) {
      commentableRanges.value = emptyList()
      return
    }

    val transferredRanges: List<LineRange> = ranges.map {
      val onesideStartLeft = viewer.transferLineToOnesideStrict(Side.LEFT, it.start1)
      if (onesideStartLeft < 0) return@map null

      val onesideStartRight = viewer.transferLineToOnesideStrict(Side.RIGHT, it.start2)
      if (onesideStartRight < 0) return@map null

      val onesideEndLeft = viewer.transferLineToOnesideStrict(Side.LEFT, it.end1 - 1) + 1
      if (onesideEndLeft < 0) return@map null

      val onesideEndRight = viewer.transferLineToOnesideStrict(Side.RIGHT, it.end2 - 1) + 1
      if (onesideEndRight < 0) return@map null
      LineRange(min(onesideStartLeft, onesideStartRight), max(onesideEndLeft, onesideEndRight))
    }.filterNotNull()
    commentableRanges.value = transferredRanges
  }

  override fun showThreads(threads: List<GHPRDiffReviewThreadMapping>?) {
    editorThreads.update(threads
                           ?.groupBy({ viewer.transferLineToOneside(it.diffSide, it.fileLineIndex) }, { it.thread })
                           ?.filterKeys { it >= 0 }.orEmpty())
  }
}




