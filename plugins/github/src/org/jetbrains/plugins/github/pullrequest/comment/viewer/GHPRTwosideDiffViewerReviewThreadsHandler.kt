// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.codereview.diff.EditorComponentInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.GHPRCommentsUtil
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewThreadMapping
import org.jetbrains.plugins.github.pullrequest.comment.ui.*
import org.jetbrains.plugins.github.ui.util.SingleValueModel

class GHPRTwosideDiffViewerReviewThreadsHandler(reviewProcessModel: GHPRReviewProcessModel,
                                                commentableRangesModel: SingleValueModel<List<Range>?>,
                                                reviewThreadsModel: SingleValueModel<List<GHPRDiffReviewThreadMapping>?>,
                                                viewer: TwosideTextDiffViewer,
                                                componentsFactory: GHPRDiffEditorReviewComponentsFactory,
                                                cumulative: Boolean)
  : GHPRDiffViewerBaseReviewThreadsHandler<TwosideTextDiffViewer>(commentableRangesModel, reviewThreadsModel, viewer) {

  private val commentableRangesLeft = SingleValueModel<List<LineRange>>(emptyList())
  private val editorThreadsLeft = GHPREditorReviewThreadsModel()

  private val commentableRangesRight = SingleValueModel<List<LineRange>>(emptyList())
  private val editorThreadsRight = GHPREditorReviewThreadsModel()

  override val viewerReady = true

  init {
    val inlaysManagerLeft = EditorComponentInlaysManager(viewer.editor1 as EditorImpl)

    val gutterIconRendererFactoryLeft = GHPRDiffEditorGutterIconRendererFactoryImpl(reviewProcessModel,
                                                                                    inlaysManagerLeft,
                                                                                    componentsFactory,
                                                                                    cumulative) { line ->
      val (startLine, endLine) = getCommentLinesRange(viewer.editor1, line)
      GHPRCommentLocation(Side.LEFT, endLine, startLine)
    }

    GHPREditorCommentableRangesController(commentableRangesLeft, gutterIconRendererFactoryLeft, viewer.editor1)
    GHPREditorReviewThreadsController(editorThreadsLeft, componentsFactory, inlaysManagerLeft)

    val inlaysManagerRight = EditorComponentInlaysManager(viewer.editor2 as EditorImpl)

    val gutterIconRendererFactoryRight = GHPRDiffEditorGutterIconRendererFactoryImpl(reviewProcessModel,
                                                                                     inlaysManagerRight,
                                                                                     componentsFactory,
                                                                                     cumulative) { line ->
      val (startLine, endLine) = getCommentLinesRange(viewer.editor2, line)
      GHPRCommentLocation(Side.RIGHT, endLine, startLine)
    }

    GHPREditorCommentableRangesController(commentableRangesRight, gutterIconRendererFactoryRight, viewer.editor2)
    GHPREditorReviewThreadsController(editorThreadsRight, componentsFactory, inlaysManagerRight)
  }

  override fun markCommentableRanges(ranges: List<Range>?) {
    commentableRangesLeft.value = ranges?.let { GHPRCommentsUtil.getLineRanges(it, Side.LEFT) }.orEmpty()
    commentableRangesRight.value = ranges?.let { GHPRCommentsUtil.getLineRanges(it, Side.RIGHT) }.orEmpty()
  }

  override fun showThreads(threads: List<GHPRDiffReviewThreadMapping>?) {
    editorThreadsLeft.update(threads
                               ?.filter { it.diffSide == Side.LEFT }
                               ?.groupBy({ it.fileLineIndex }, { it.thread }).orEmpty())
    editorThreadsRight.update(threads
                                ?.filter { it.diffSide == Side.RIGHT }
                                ?.groupBy({ it.fileLineIndex }, { it.thread }).orEmpty())
  }
}
