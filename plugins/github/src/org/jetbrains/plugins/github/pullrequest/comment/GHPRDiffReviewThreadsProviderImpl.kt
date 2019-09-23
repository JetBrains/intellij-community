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
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRDiffViewerBaseReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRSimpleOnesideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRTwosideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRUnifiedDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.util.handleOnEdt

class GHPRDiffReviewThreadsProviderImpl(private val dataProvider: GithubPullRequestDataProvider,
                                        private val componentFactory: GHPREditorReviewThreadComponentFactory)
  : GHPRDiffReviewThreadsProvider {

  override fun install(viewer: DiffViewerBase, change: Change) {
    val commentsHandler = when (viewer) {
      is SimpleOnesideDiffViewer ->
        GHPRSimpleOnesideDiffViewerReviewThreadsHandler(viewer, componentFactory)
      is UnifiedDiffViewer ->
        GHPRUnifiedDiffViewerReviewThreadsHandler(viewer, componentFactory)
      is TwosideTextDiffViewer ->
        GHPRTwosideDiffViewerReviewThreadsHandler(viewer, componentFactory)
      else -> return
    }
    Disposer.register(viewer, commentsHandler)

    loadAndShowComments(commentsHandler, change)
    dataProvider.addRequestsChangesListener(commentsHandler, object : GithubPullRequestDataProvider.RequestsChangedListener {
      override fun reviewThreadsRequestChanged() {
        loadAndShowComments(commentsHandler, change)
      }
    })
  }

  private fun loadAndShowComments(commentsHandler: GHPRDiffViewerBaseReviewThreadsHandler<out ListenerDiffViewerBase>,
                                  change: Change) {
    val disposable = Disposer.newDisposable()
    dataProvider.filesReviewThreadsRequest.handleOnEdt(disposable) { result, error ->
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
    val LOG = logger<GHPRDiffReviewThreadsProviderImpl>()
  }
}