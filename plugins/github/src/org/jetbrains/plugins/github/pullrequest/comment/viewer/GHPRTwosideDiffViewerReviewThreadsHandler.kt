// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.GHPRCommentsUtil
import org.jetbrains.plugins.github.pullrequest.comment.ui.*

class GHPRTwosideDiffViewerReviewThreadsHandler(reviewProcessModel: GHPRReviewProcessModel,
                                                commentableRangesModel: SingleValueModel<List<Range>?>,
                                                reviewThreadsModel: SingleValueModel<List<DiffMappedValue<GHPullRequestReviewThread>>?>,
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
    val gutterIconRendererFactoryLeft = GHPRDiffEditorGutterIconRendererFactoryImpl(reviewProcessModel,
                                                                                    viewer.editor1,
                                                                                    componentsFactory,
                                                                                    cumulative) { line ->
      val (startLine, endLine) = getCommentLinesRange(viewer.editor1, line)
      GHPRCommentLocation(Side.LEFT, endLine, startLine)
    }

    GHPREditorCommentableRangesController(commentableRangesLeft, gutterIconRendererFactoryLeft, viewer.editor1)
    GHPREditorReviewThreadsController(editorThreadsLeft, componentsFactory, viewer.editor1)

    val gutterIconRendererFactoryRight = GHPRDiffEditorGutterIconRendererFactoryImpl(reviewProcessModel,
                                                                                     viewer.editor2,
                                                                                     componentsFactory,
                                                                                     cumulative) { line ->
      val (startLine, endLine) = getCommentLinesRange(viewer.editor2, line)
      GHPRCommentLocation(Side.RIGHT, endLine, startLine)
    }

    GHPREditorCommentableRangesController(commentableRangesRight, gutterIconRendererFactoryRight, viewer.editor2)
    GHPREditorReviewThreadsController(editorThreadsRight, componentsFactory, viewer.editor2)
  }

  override fun markCommentableRanges(ranges: List<Range>?) {
    commentableRangesLeft.value = ranges?.let { GHPRCommentsUtil.getLineRanges(it, Side.LEFT) }.orEmpty()
    commentableRangesRight.value = ranges?.let { GHPRCommentsUtil.getLineRanges(it, Side.RIGHT) }.orEmpty()
  }

  override fun showThreads(threads: List<DiffMappedValue<GHPullRequestReviewThread>>?) {
    editorThreadsLeft.update(threads
                               ?.filter { it.side == Side.LEFT }
                               ?.groupBy({ it.lineIndex }, { it.value }).orEmpty())
    editorThreadsRight.update(threads
                                ?.filter { it.side == Side.RIGHT }
                                ?.groupBy({ it.lineIndex }, { it.value }).orEmpty())
  }
}
