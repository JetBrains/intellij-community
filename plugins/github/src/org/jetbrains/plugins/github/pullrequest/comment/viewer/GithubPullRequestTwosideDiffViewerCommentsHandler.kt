// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.EditorComponentInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadsController
import org.jetbrains.plugins.github.pullrequest.comment.ui.model.GithubPullRequestFileCommentsThreadsMap
import org.jetbrains.plugins.github.pullrequest.data.model.GithubPullRequestFileCommentsThreadMapping

class GithubPullRequestTwosideDiffViewerCommentsHandler(viewer: TwosideTextDiffViewer,
                                                        componentFactory: GithubPullRequestEditorCommentsThreadComponentFactory)
  : GithubPullRequestDiffViewerBaseCommentsHandler<TwosideTextDiffViewer>(viewer, componentFactory) {

  private val editorsThreads: Map<Side, GithubPullRequestFileCommentsThreadsMap>

  override val viewerReady = true

  init {
    val editorThreadsLeft = GithubPullRequestFileCommentsThreadsMap()
    GithubPullRequestEditorCommentsThreadsController(editorThreadsLeft, componentFactory,
                                                     EditorComponentInlaysManager(viewer.editor1 as EditorImpl))

    val editorThreadsRight = GithubPullRequestFileCommentsThreadsMap()
    GithubPullRequestEditorCommentsThreadsController(editorThreadsRight, componentFactory,
                                                     EditorComponentInlaysManager(viewer.editor2 as EditorImpl))

    editorsThreads = mapOf(Side.LEFT to editorThreadsLeft, Side.RIGHT to editorThreadsRight)
  }

  override fun updateThreads(mappings: List<GithubPullRequestFileCommentsThreadMapping>) {
    mappings.groupBy { it.side }.forEach { (side, mappings) ->
      editorsThreads[side]?.update(mappings.groupBy { it.fileLine })
    }
  }
}

