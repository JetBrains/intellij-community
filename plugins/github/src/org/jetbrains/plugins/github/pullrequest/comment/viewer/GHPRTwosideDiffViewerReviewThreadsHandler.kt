// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.EditorComponentInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsModel
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping

class GHPRTwosideDiffViewerReviewThreadsHandler(viewer: TwosideTextDiffViewer,
                                                componentFactory: GHPREditorReviewThreadComponentFactory)
  : GHPRDiffViewerBaseReviewThreadsHandler<TwosideTextDiffViewer>(viewer, componentFactory) {

  private val editorsThreads: Map<Side, GHPREditorReviewThreadsModel>

  override val viewerReady = true

  init {
    val editorThreadsLeft = GHPREditorReviewThreadsModel()
    GHPREditorReviewThreadsController(editorThreadsLeft, componentFactory,
                                      EditorComponentInlaysManager(viewer.editor1 as EditorImpl))

    val editorThreadsRight = GHPREditorReviewThreadsModel()
    GHPREditorReviewThreadsController(editorThreadsRight, componentFactory,
                                      EditorComponentInlaysManager(viewer.editor2 as EditorImpl))

    editorsThreads = mapOf(Side.LEFT to editorThreadsLeft, Side.RIGHT to editorThreadsRight)
  }

  override fun updateThreads(mappings: List<GHPRDiffReviewThreadMapping>) {
    mappings.groupBy { it.side }.forEach { (side, mappings) ->
      editorsThreads[side]?.update(mappings.groupBy { it.fileLine })
    }
  }
}

