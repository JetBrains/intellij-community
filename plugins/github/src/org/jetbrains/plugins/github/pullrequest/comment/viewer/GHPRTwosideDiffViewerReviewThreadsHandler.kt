// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRDiffEditorReviewComponentsFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffRangeMapping
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping
import org.jetbrains.plugins.github.ui.util.SingleValueModel

class GHPRTwosideDiffViewerReviewThreadsHandler(viewer: TwosideTextDiffViewer,
                                                reviewService: GHPRReviewServiceAdapter,
                                                componentFactory: GHPRDiffEditorReviewComponentsFactory)
  : GHPRDiffViewerBaseReviewThreadsHandler<TwosideTextDiffViewer>() {

  private val editorsThreads: Map<Side, GHPREditorReviewThreadsModel>
  private val editorsCommentableRanges: Map<Side, SingleValueModel<List<GHPRDiffRangeMapping>>>

  override val viewerReady = true

  init {
    val editorThreadsLeft = GHPREditorReviewThreadsModel()
    val editorCommentableRangesLeft = SingleValueModel<List<GHPRDiffRangeMapping>>(emptyList())
    GHPREditorReviewThreadsController(editorThreadsLeft, editorCommentableRangesLeft,
                                      reviewService, componentFactory, viewer.editor1)

    val editorThreadsRight = GHPREditorReviewThreadsModel()
    val editorCommentableRangesRight = SingleValueModel<List<GHPRDiffRangeMapping>>(emptyList())
    GHPREditorReviewThreadsController(editorThreadsRight, editorCommentableRangesRight,
                                      reviewService, componentFactory, viewer.editor2)

    editorsThreads = mapOf(Side.LEFT to editorThreadsLeft, Side.RIGHT to editorThreadsRight)
    editorsCommentableRanges = mapOf(Side.LEFT to editorCommentableRangesLeft, Side.RIGHT to editorCommentableRangesRight)
  }

  override fun updateCommentableRanges(ranges: List<GHPRDiffRangeMapping>) {
    ranges.groupBy { it.side }.forEach { (side, ranges) ->
      editorsCommentableRanges[side]?.value = ranges
    }
  }

  override fun updateThreads(mappings: List<GHPRDiffReviewThreadMapping>) {
    mappings.groupBy { it.side }.forEach { (side, mappings) ->
      editorsThreads[side]?.update(mappings.groupBy { it.fileLineIndex })
    }
  }
}

