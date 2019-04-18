// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.fragmented.UnifiedDiffPanel
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.comment.model.GithubPullRequestFileCommentThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.ComponentEditorInlaysManager
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestDiffCommentComponentFactory

class GithubPullRequestUnifiedDiffViewerCommentsHandler(viewer: UnifiedDiffViewer,
                                                        private val componentFactory: GithubPullRequestDiffCommentComponentFactory)
  : GithubPullRequestDiffViewerBaseCommentsHandler<UnifiedDiffViewer>(viewer) {

  private val editorInlaysManager = ComponentEditorInlaysManager(viewer.editor as EditorImpl)

  override val viewerReady: Boolean
    get() = (viewer.component as? UnifiedDiffPanel)?.isGoodContent ?: false

  init {
    viewer.addListener(object : DiffViewerListener() {
      override fun onAfterRediff() {
        threads?.also { showCommentThreads(it) }
      }
    })
  }

  override fun showCommentThreads(commentThreads: List<GithubPullRequestFileCommentThread>) {
    if (commentThreads.isEmpty()) return
    for ((line, threads) in commentThreads.mergeSidesAndExpand(viewer)) {
      editorInlaysManager.addComponent(line, componentFactory.createComponent(threads))
    }
  }

  private fun List<GithubPullRequestFileCommentThread>.mergeSidesAndExpand(viewer: UnifiedDiffViewer): Map<Int, List<List<GithubPullRequestCommentWithHtml>>> {
    return groupBy { viewer.transferLineToOneside(it.side, it.fileLine) }
      .filter { (line, _) -> line > 0 }
      .mapValues { (_, threads) -> threads.map { it.comments } }
  }
}




