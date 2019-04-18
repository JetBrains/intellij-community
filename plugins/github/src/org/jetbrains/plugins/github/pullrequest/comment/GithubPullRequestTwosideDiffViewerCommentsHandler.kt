// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.comment.model.GithubPullRequestFileCommentThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.ComponentEditorInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestDiffCommentComponentFactory

class GithubPullRequestTwosideDiffViewerCommentsHandler(viewer: TwosideTextDiffViewer,
                                                        private val componentFactory: GithubPullRequestDiffCommentComponentFactory)
  : GithubPullRequestDiffViewerBaseCommentsHandler<TwosideTextDiffViewer>(viewer) {

  override val viewerReady = true

  private val inlaysManagers = mapOf(Side.LEFT to ComponentEditorInlaysManager(viewer.editor1 as EditorImpl),
                                     Side.RIGHT to ComponentEditorInlaysManager(viewer.editor2 as EditorImpl))

  override fun showCommentThreads(commentThreads: List<GithubPullRequestFileCommentThread>) {
    val sided = commentThreads.groupBy { it.side }
    sided[Side.LEFT]?.also { showCommentThreads(Side.LEFT, it.expand()) }
    sided[Side.RIGHT]?.also { showCommentThreads(Side.RIGHT, it.expand()) }
  }

  private fun showCommentThreads(side: Side, threadsByLines: Map<Int, List<List<GithubPullRequestCommentWithHtml>>>) {
    val manager = inlaysManagers[side] ?: return
    for ((line, threads) in threadsByLines) {
      manager.addComponent(line, componentFactory.createComponent(threads))
    }
  }

  private fun List<GithubPullRequestFileCommentThread>.expand() =
    groupBy { it.fileLine }.mapValues { (_, threads) -> threads.map { it.comments } }
}

