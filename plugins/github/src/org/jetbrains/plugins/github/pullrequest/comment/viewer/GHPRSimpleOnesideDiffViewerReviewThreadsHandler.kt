// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRDiffEditorReviewComponentsFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffRangeMapping
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping
import org.jetbrains.plugins.github.ui.util.SingleValueModel

class GHPRSimpleOnesideDiffViewerReviewThreadsHandler(private val viewer: SimpleOnesideDiffViewer,
                                                      reviewService: GHPRReviewServiceAdapter,
                                                      componentFactory: GHPRDiffEditorReviewComponentsFactory)
  : GHPRDiffViewerBaseReviewThreadsHandler<SimpleOnesideDiffViewer>() {

  private val editorThreads = GHPREditorReviewThreadsModel()
  private val editorCommentableRanges = SingleValueModel<List<GHPRDiffRangeMapping>>(emptyList())

  override val viewerReady: Boolean = true

  init {
    GHPREditorReviewThreadsController(editorThreads, editorCommentableRanges, reviewService, componentFactory, viewer.editor)
  }

  override fun updateThreads(mappings: List<GHPRDiffReviewThreadMapping>) {
    editorThreads.update(mappings.filter { it.side == viewer.side }.groupBy { it.fileLineIndex })
  }

  override fun updateCommentableRanges(ranges: List<GHPRDiffRangeMapping>) {
    editorCommentableRanges.value = ranges.filter { it.side == viewer.side }
  }
}
