// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.EditorComponentInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadsModel
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping

class GHPRSimpleOnesideDiffViewerReviewThreadsHandler(viewer: SimpleOnesideDiffViewer,
                                                      componentFactory: GHPREditorReviewThreadComponentFactory)
  : GHPRDiffViewerBaseReviewThreadsHandler<SimpleOnesideDiffViewer>(viewer, componentFactory) {

  private val editorThreads = GHPREditorReviewThreadsModel()

  override val viewerReady: Boolean = true

  init {
    val inlaysManager = EditorComponentInlaysManager(viewer.editor as EditorImpl)
    GHPREditorReviewThreadsController(editorThreads, componentFactory, inlaysManager)
  }

  override fun updateThreads(mappings: List<GHPRDiffReviewThreadMapping>) {
    editorThreads.update(mappings.filter { it.side == viewer.side }.groupBy { it.fileLine })
  }
}
