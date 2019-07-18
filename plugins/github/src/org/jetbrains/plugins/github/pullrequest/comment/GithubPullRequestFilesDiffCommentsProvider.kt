// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.plugins.github.pullrequest.comment.ui.GithubPullRequestEditorCommentsThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GithubPullRequestDiffViewerBaseCommentsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GithubPullRequestSimpleOnesideDiffViewerCommentsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GithubPullRequestTwosideDiffViewerCommentsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GithubPullRequestUnifiedDiffViewerCommentsHandler
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.util.handleOnEdt

class GithubPullRequestFilesDiffCommentsProvider(private val dataProvider: GithubPullRequestDataProvider,
                                                 private val componentFactory: GithubPullRequestEditorCommentsThreadComponentFactory)
  : GithubPullRequestDiffCommentsProvider {

  override fun install(viewer: DiffViewerBase, change: Change) {
    val commentsHandler = when (viewer) {
      is SimpleOnesideDiffViewer ->
        GithubPullRequestSimpleOnesideDiffViewerCommentsHandler(viewer, componentFactory)
      is UnifiedDiffViewer ->
        GithubPullRequestUnifiedDiffViewerCommentsHandler(viewer, componentFactory)
      is TwosideTextDiffViewer ->
        GithubPullRequestTwosideDiffViewerCommentsHandler(viewer, componentFactory)
      else -> return
    }
    Disposer.register(viewer, commentsHandler)

    loadAndShowComments(commentsHandler, change)
    dataProvider.addRequestsChangesListener(commentsHandler, object : GithubPullRequestDataProvider.RequestsChangedListener {
      override fun commentsRequestChanged() {
        loadAndShowComments(commentsHandler, change)
      }
    })
  }

  private fun loadAndShowComments(commentsHandler: GithubPullRequestDiffViewerBaseCommentsHandler<out ListenerDiffViewerBase>,
                                  change: Change) {
    val disposable = Disposer.newDisposable()
    dataProvider.filesCommentThreadsRequest.handleOnEdt(disposable) { result, error ->
      if (result != null) {
        commentsHandler.mappings = result[change].orEmpty()
      }
      if (error != null) {
        LOG.info("Failed to load and process file comments", error)
      }
    }
    Disposer.register(commentsHandler, disposable)
  }

  companion object {
    val LOG = logger<GithubPullRequestFilesDiffCommentsProvider>()
  }
}