// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestDiffCommentComponentFactory
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.util.handleOnEdt

class GithubPullRequestFilesDiffCommentsProvider(private val dataProvider: GithubPullRequestDataProvider,
                                                 private val componentFactory: GithubPullRequestDiffCommentComponentFactory)
  : GithubPullRequestDiffCommentsProvider {

  init {
    //Disposer.register(dataProvider, this)
  }

  override fun installComments(viewer: ListenerDiffViewerBase, change: Change) {
    if (Disposer.isDisposed(this)) return

    val commentsHandler = when (viewer) {
      is UnifiedDiffViewer -> GithubPullRequestUnifiedDiffViewerCommentsHandler(viewer, componentFactory)
      is TwosideTextDiffViewer -> GithubPullRequestTwosideDiffViewerCommentsHandler(viewer, componentFactory)
      else -> return
    }

    //TODO: refresh
    dataProvider.filesCommentThreadsRequest.handleOnEdt(commentsHandler) { result, error ->
      if (result != null) {
        commentsHandler.threads = result.filter { it.change == change }
      }
      if (error != null) {
        LOG.info("Failed to load and process file comments", error)
      }
    }
  }

  override fun dispose() {}

  companion object {
    val LOG = logger<GithubPullRequestFilesDiffCommentsProvider>()
  }
}