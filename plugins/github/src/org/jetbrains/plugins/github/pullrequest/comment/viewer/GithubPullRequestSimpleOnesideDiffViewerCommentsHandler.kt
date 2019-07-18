// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.EditorComponentInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileCommentsThreadsMap
import org.jetbrains.plugins.github.pullrequest.data.model.GithubPullRequestFileCommentsThreadMapping

class GithubPullRequestSimpleOnesideDiffViewerCommentsHandler(viewer: SimpleOnesideDiffViewer,
                                                              componentFactory: GithubPullRequestEditorCommentsThreadComponentFactory)
  : GithubPullRequestDiffViewerBaseCommentsHandler<SimpleOnesideDiffViewer>(viewer, componentFactory) {

  private val editorThreads = GithubPullRequestFileCommentsThreadsMap()

  override val viewerReady: Boolean = true

  init {
    val inlaysManager = EditorComponentInlaysManager(viewer.editor as EditorImpl)
    GithubPullRequestEditorCommentsThreadsController(editorThreads, componentFactory, inlaysManager)
  }

  override fun updateThreads(mappings: List<GithubPullRequestFileCommentsThreadMapping>) {
    editorThreads.update(mappings.filter { it.side == viewer.side }.groupBy { it.fileLine })
  }
}
