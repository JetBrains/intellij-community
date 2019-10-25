// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Range
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRDiffEditorReviewComponentsFactoryImpl
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRSimpleOnesideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRTwosideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRUnifiedDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangedFileLinesMapper
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.handleOnEdt

class GHPRDiffReviewSupportImpl(private val project: Project,
                                private val reviewService: GHPRReviewServiceAdapter,
                                private val diffRanges: List<Range>,
                                private val fileLinesMapper: GHPRChangedFileLinesMapper,
                                private val lastCommitSha: String,
                                private val filePath: String,
                                private val reviewThreadsFilter: (String, String) -> Boolean,
                                private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                private val currentUser: GHUser)
  : GHPRDiffReviewSupport {

  override fun install(viewer: DiffViewerBase) {
    val diffRangesModel = SingleValueModel(if (reviewService.canComment()) diffRanges else null)
    val reviewThreadsModel = SingleValueModel<List<GHPullRequestReviewThread>?>(null)
    loadReviewThreads(reviewThreadsModel, viewer)

    val componentsFactory = GHPRDiffEditorReviewComponentsFactoryImpl(project, reviewService, lastCommitSha, filePath,
                                                                      avatarIconsProviderFactory, currentUser)
    when (viewer) {
      is SimpleOnesideDiffViewer ->
        GHPRSimpleOnesideDiffViewerReviewThreadsHandler(diffRangesModel, reviewThreadsModel, viewer, fileLinesMapper, componentsFactory)
      is UnifiedDiffViewer ->
        GHPRUnifiedDiffViewerReviewThreadsHandler(diffRangesModel, reviewThreadsModel, viewer, fileLinesMapper, componentsFactory)
      is TwosideTextDiffViewer ->
        GHPRTwosideDiffViewerReviewThreadsHandler(diffRangesModel, reviewThreadsModel, viewer, fileLinesMapper, componentsFactory)
      else -> return
    }
  }

  private fun loadReviewThreads(threadsModel: SingleValueModel<List<GHPullRequestReviewThread>?>, disposable: Disposable) {
    doLoadReviewThreads(threadsModel, disposable)
    reviewService.addReviewThreadsListener(disposable) {
      doLoadReviewThreads(threadsModel, disposable)
    }
  }

  private fun doLoadReviewThreads(threadsModel: SingleValueModel<List<GHPullRequestReviewThread>?>, disposable: Disposable) {
    reviewService.loadReviewThreads().handleOnEdt(disposable) { result, error ->
      if (result != null) {
        threadsModel.value = result.filter { it.position != null && reviewThreadsFilter(it.commit.oid, it.path) }
      }
      if (error != null) {
        LOG.info("Failed to load review threads", error)
      }
    }
  }

  companion object {
    val LOG = logger<GHPRDiffReviewSupportImpl>()
  }
}