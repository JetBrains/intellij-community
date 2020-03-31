// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Range
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRDiffEditorReviewComponentsFactoryImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewProcessModelImpl
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRSimpleOnesideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRTwosideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRUnifiedDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHSimpleLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCreateDiffCommentParametersHelper
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.successAsync
import kotlin.properties.Delegates.observable

class GHPRDiffReviewSupportImpl(private val reviewDataProvider: GHPRReviewDataProvider,
                                private val diffRanges: List<Range>,
                                private val reviewThreadMapper: (GHPullRequestReviewThread) -> GHPRDiffReviewThreadMapping?,
                                private val createCommentParametersHelper: GHPRCreateDiffCommentParametersHelper,
                                private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                private val currentUser: GHUser)
  : GHPRDiffReviewSupport {

  private var pendingReviewLoadingModel: GHSimpleLoadingModel<GHPullRequestPendingReview?>? = null
  private val reviewProcessModel = GHPRReviewProcessModelImpl()

  private var reviewThreadsLoadingModel: GHSimpleLoadingModel<List<GHPRDiffReviewThreadMapping>>? = null
  private val reviewThreadsModel = SingleValueModel<List<GHPRDiffReviewThreadMapping>?>(null)

  override val isLoadingReviewData: Boolean
    get() = reviewThreadsLoadingModel?.loading == true || pendingReviewLoadingModel?.loading == true

  override var showReviewThreads by observable(true) { _, _, _ ->
    updateReviewThreads()
  }

  override var showResolvedReviewThreads by observable(false) { _, _, _ ->
    updateReviewThreads()
  }

  override fun install(viewer: DiffViewerBase) {
    val diffRangesModel = SingleValueModel(if (reviewDataProvider.canComment()) diffRanges else null)

    if (reviewDataProvider.canComment()) {
      loadPendingReview(viewer)
      var rangesInstalled = false
      reviewProcessModel.addAndInvokeChangesListener(object : SimpleEventListener {
        override fun eventOccurred() {
          if (reviewProcessModel.isActual && !rangesInstalled) {
            diffRangesModel.value = diffRanges
            rangesInstalled = true
          }
        }
      })
    }

    loadReviewThreads(viewer)

    val componentsFactory = GHPRDiffEditorReviewComponentsFactoryImpl(reviewDataProvider,
                                                                      createCommentParametersHelper,
                                                                      avatarIconsProviderFactory, currentUser)
    when (viewer) {
      is SimpleOnesideDiffViewer ->
        GHPRSimpleOnesideDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory)
      is UnifiedDiffViewer ->
        GHPRUnifiedDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory)
      is TwosideTextDiffViewer ->
        GHPRTwosideDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory)
      else -> return
    }
  }

  override fun reloadReviewData() {
    reviewDataProvider.resetPendingReview()
    reviewDataProvider.resetReviewThreads()
  }

  private fun loadPendingReview(disposable: Disposable) {
    val loadingModel = GHCompletableFutureLoadingModel<GHPullRequestPendingReview?>(disposable).also {
      it.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
        override fun onLoadingCompleted() {
          if (it.resultAvailable) {
            reviewProcessModel.populatePendingReviewData(it.result)
          }
        }
      })
    }
    pendingReviewLoadingModel = loadingModel

    doLoadPendingReview(loadingModel)
    reviewDataProvider.addPendingReviewListener(disposable) {
      reviewProcessModel.clearPendingReviewData()
      doLoadPendingReview(loadingModel)
    }
  }

  private fun doLoadPendingReview(model: GHCompletableFutureLoadingModel<GHPullRequestPendingReview?>) {
    model.future = reviewDataProvider.loadPendingReview()
  }

  private fun loadReviewThreads(disposable: Disposable) {
    val loadingModel = GHCompletableFutureLoadingModel<List<GHPRDiffReviewThreadMapping>>(disposable).apply {
      addStateChangeListener(object : GHLoadingModel.StateChangeListener {
        override fun onLoadingCompleted() = updateReviewThreads()
      })
    }
    reviewThreadsLoadingModel = loadingModel

    doLoadReviewThreads(loadingModel)
    reviewDataProvider.addReviewThreadsListener(disposable) {
      doLoadReviewThreads(loadingModel)
    }
  }

  private fun doLoadReviewThreads(model: GHCompletableFutureLoadingModel<List<GHPRDiffReviewThreadMapping>>) {
    model.future = reviewDataProvider.loadReviewThreads().successAsync(ProcessIOExecutorService.INSTANCE) {
      it.mapNotNull(reviewThreadMapper)
    }
  }

  private fun updateReviewThreads() {
    val loadingModel = reviewThreadsLoadingModel ?: return
    if (loadingModel.loading) return
    reviewThreadsModel.value = if (showReviewThreads) loadingModel.result?.filter { showResolvedReviewThreads || !it.thread.isResolved } else null
  }
}