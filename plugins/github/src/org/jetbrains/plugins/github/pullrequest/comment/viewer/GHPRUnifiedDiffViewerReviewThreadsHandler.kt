// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerListener
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewCommentsComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsModel
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffRangeMapping
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping
import org.jetbrains.plugins.github.ui.util.SingleValueModel

class GHPRUnifiedDiffViewerReviewThreadsHandler(private val viewer: UnifiedDiffViewer,
                                                reviewService: GHPRReviewServiceAdapter,
                                                componentFactory: GHPREditorReviewCommentsComponentFactory)
  : GHPRDiffViewerBaseReviewThreadsHandler<UnifiedDiffViewer>() {

  private val editorThreads = GHPREditorReviewThreadsModel()
  private val editorCommentableRanges = SingleValueModel<List<GHPRDiffRangeMapping>>(emptyList())

  override val viewerReady: Boolean
    get() = viewer.isContentGood

  init {
    GHPREditorReviewThreadsController(editorThreads, editorCommentableRanges, reviewService, componentFactory, viewer.editor)

    viewer.addListener(object : DiffViewerListener() {
      override fun onAfterRediff() {
        updateThreads(reviewThreadsMappings)
        updateCommentableRanges(commentableRangesMappings)
      }
    })
  }

  override fun updateCommentableRanges(ranges: List<GHPRDiffRangeMapping>) {
    val transferredRanges = ranges.map {
      val onesideStart = viewer.transferLineToOnesideStrict(it.side, it.start)
      if (onesideStart < 0) return@map null

      val onesideOffset = onesideStart - it.start

      val onesideEnd = viewer.transferLineToOneside(it.side, it.end - 1) + 1
      //incorrect mapping - range must stay the same size
      if (onesideOffset != onesideEnd - it.end) return@map null

      GHPRDiffRangeMapping(it.commitSha, it.filePath, it.side,
                           onesideStart,
                           onesideEnd,
                           it.offset - onesideOffset)
    }.filterNotNull().sortedBy { it.end }
    val fixedRanges = mutableListOf<GHPRDiffRangeMapping>()
    for (range in transferredRanges) {
      val lastRange = fixedRanges.lastOrNull()
      if (lastRange != null && lastRange.end > range.start) {
        if (lastRange.end != range.end)
          fixedRanges.add(GHPRDiffRangeMapping(range.commitSha, range.filePath, range.side, lastRange.end, range.end, range.offset))
      }
      else {
        fixedRanges.add(range)
      }
    }

    editorCommentableRanges.value = fixedRanges
  }

  override fun updateThreads(mappings: List<GHPRDiffReviewThreadMapping>) {
    editorThreads.update(mappings.groupBy { viewer.transferLineToOneside(it.side, it.fileLineIndex) }.filter { (line, _) -> line >= 0 })
  }
}




