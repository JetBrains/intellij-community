// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.GHPRCommentsUtil
import org.jetbrains.plugins.github.pullrequest.comment.ui.*
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangedFileLinesMapper
import org.jetbrains.plugins.github.ui.util.SingleValueModel

class GHPRTwosideDiffViewerReviewThreadsHandler(commentableRangesModel: SingleValueModel<List<Range>?>,
                                                reviewThreadsModel: SingleValueModel<List<GHPullRequestReviewThread>?>,
                                                viewer: TwosideTextDiffViewer,
                                                private val linesMapper: GHPRChangedFileLinesMapper,
                                                componentsFactory: GHPRDiffEditorReviewComponentsFactory)
  : GHPRDiffViewerBaseReviewThreadsHandler<TwosideTextDiffViewer>(commentableRangesModel, reviewThreadsModel, viewer) {

  private val commentableRangesLeft = SingleValueModel<List<LineRange>>(emptyList())
  private val editorThreadsLeft = GHPREditorReviewThreadsModel()

  private val commentableRangesRight = SingleValueModel<List<LineRange>>(emptyList())
  private val editorThreadsRight = GHPREditorReviewThreadsModel()

  override val viewerReady = true

  init {
    val inlaysManagerLeft = EditorComponentInlaysManager(viewer.editor1 as EditorImpl)

    GHPREditorCommentableRangesController(commentableRangesLeft, componentsFactory, inlaysManagerLeft) {
      linesMapper.findDiffLine(Side.LEFT, it)
    }
    GHPREditorReviewThreadsController(editorThreadsLeft, componentsFactory, inlaysManagerLeft)

    val inlaysManagerRight = EditorComponentInlaysManager(viewer.editor2 as EditorImpl)

    GHPREditorCommentableRangesController(commentableRangesRight, componentsFactory, inlaysManagerRight) {
      linesMapper.findDiffLine(Side.RIGHT, it)
    }
    GHPREditorReviewThreadsController(editorThreadsRight, componentsFactory, inlaysManagerRight)
  }

  override fun markCommentableRanges(ranges: List<Range>?) {
    commentableRangesLeft.value = ranges?.let { GHPRCommentsUtil.getLineRanges(it, Side.LEFT) }.orEmpty()
    commentableRangesRight.value = ranges?.let { GHPRCommentsUtil.getLineRanges(it, Side.RIGHT) }.orEmpty()
  }

  override fun showThreads(threads: List<GHPullRequestReviewThread>?) {
    val mappedThreads = threads?.let { GHPRCommentsUtil.mapThreadsToLines(linesMapper, it) }.orEmpty()
    editorThreadsLeft.update(mappedThreads
                               .filterKeys { it.first == Side.LEFT }
                               .mapKeys { it.key.second })
    editorThreadsRight.update(mappedThreads
                                .filterKeys { it.first == Side.RIGHT }
                                .mapKeys { it.key.second })
  }
}

