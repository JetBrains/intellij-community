// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.GHPRCommentsUtil
import org.jetbrains.plugins.github.pullrequest.comment.ui.*

class GHPRSimpleOnesideDiffViewerReviewThreadsHandler(reviewProcessModel: GHPRReviewProcessModel,
                                                      commentableRangesModel: SingleValueModel<List<Range>?>,
                                                      reviewThreadsModel: SingleValueModel<List<DiffMappedValue<GHPullRequestReviewThread>>?>,
                                                      viewer: SimpleOnesideDiffViewer,
                                                      componentsFactory: GHPRDiffEditorReviewComponentsFactory,
                                                      cumulative: Boolean)
  : GHPRDiffViewerBaseReviewThreadsHandler<SimpleOnesideDiffViewer>(commentableRangesModel, reviewThreadsModel, viewer) {

  private val commentableRanges = SingleValueModel<List<LineRange>>(emptyList())
  private val editorThreads = GHPREditorReviewThreadsModel()

  override val viewerReady: Boolean = true

  init {
    val gutterIconRendererFactory = GHPRDiffEditorGutterIconRendererFactoryImpl(reviewProcessModel,
                                                                                viewer.editor,
                                                                                componentsFactory,
                                                                                cumulative) { line ->
      val (startLine, endLine) = getCommentLinesRange(viewer.editor, line)
      GHPRCommentLocation(viewer.side, endLine, startLine)
    }

    GHPREditorCommentableRangesController(commentableRanges, gutterIconRendererFactory, viewer.editor)
    GHPREditorReviewThreadsController(editorThreads, componentsFactory, viewer.editor)
  }

  override fun markCommentableRanges(ranges: List<Range>?) {
    commentableRanges.value = ranges?.let { GHPRCommentsUtil.getLineRanges(it, viewer.side) }.orEmpty()
  }

  override fun showThreads(threads: List<DiffMappedValue<GHPullRequestReviewThread>>?) {
    editorThreads.update(threads
                           ?.filter { it.side == viewer.side }
                           ?.groupBy({ it.lineIndex }, { it.value }).orEmpty())
  }
}
