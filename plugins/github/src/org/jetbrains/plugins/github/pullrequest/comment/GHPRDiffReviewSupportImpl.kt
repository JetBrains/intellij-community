// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.filePath
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRDiffEditorReviewComponentsFactoryImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewProcessModelImpl
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRSimpleOnesideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRTwosideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRUnifiedDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHSimpleLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCreateDiffCommentParametersHelper
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.util.function.Function
import kotlin.properties.Delegates.observable

class GHPRDiffReviewSupportImpl(private val project: Project,
                                private val reviewDataProvider: GHPRReviewDataProvider,
                                private val detailsDataProvider: GHPRDetailsDataProvider,
                                private val avatarIconsProvider: GHAvatarIconsProvider,
                                private val repositoryDataService: GHPRRepositoryDataService,
                                private val diffData: GitTextFilePatchWithHistory,
                                private val ghostUser: GHUser,
                                private val currentUser: GHUser)
  : GHPRDiffReviewSupport {

  private var pendingReviewLoadingModel: GHSimpleLoadingModel<GHPullRequestPendingReview?>? = null
  private val reviewProcessModel = GHPRReviewProcessModelImpl()

  private var reviewThreadsLoadingModel: GHSimpleLoadingModel<List<DiffMappedValue<GHPullRequestReviewThread>>>? = null
  private val reviewThreadsModel = SingleValueModel<List<DiffMappedValue<GHPullRequestReviewThread>>?>(null)

  override val isLoadingReviewData: Boolean
    get() = reviewThreadsLoadingModel?.loading == true || pendingReviewLoadingModel?.loading == true

  override var showReviewThreads by observable(true) { _, _, _ ->
    updateReviewThreads()
  }

  override var showResolvedReviewThreads by observable(false) { _, _, _ ->
    updateReviewThreads()
  }

  override fun install(viewer: DiffViewerBase) {
    val diffRangesModel = SingleValueModel(if (reviewDataProvider.canComment()) diffData.diffRanges else null)

    if (reviewDataProvider.canComment()) {
      loadPendingReview(viewer)
      var rangesInstalled = false
      reviewProcessModel.addAndInvokeChangesListener {
        if (reviewProcessModel.isActual && !rangesInstalled) {
          diffRangesModel.value = diffData.diffRanges
          rangesInstalled = true
        }
      }
    }

    loadReviewThreads(viewer)

    val createCommentParametersHelper = GHPRCreateDiffCommentParametersHelper(diffData.patch.afterVersionId!!, diffData.patch.filePath)
    val suggestedChangesHelper = GHPRSuggestedChangeHelper(project,
                                                           viewer, repositoryDataService.remoteCoordinates.repository,
                                                           reviewDataProvider,
                                                           detailsDataProvider)
    val componentsFactory = GHPRDiffEditorReviewComponentsFactoryImpl(project,
                                                                      reviewDataProvider, avatarIconsProvider,
                                                                      createCommentParametersHelper, suggestedChangesHelper,
                                                                      ghostUser,
                                                                      currentUser)
    val cumulative = diffData is GitTextFilePatchWithHistory.Cumulative
    when (viewer) {
      is SimpleOnesideDiffViewer ->
        GHPRSimpleOnesideDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory,
                                                        cumulative)
      is UnifiedDiffViewer ->
        GHPRUnifiedDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory,
                                                  cumulative)
      is TwosideTextDiffViewer ->
        GHPRTwosideDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory,
                                                  cumulative)
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
    val loadingModel = GHCompletableFutureLoadingModel<List<DiffMappedValue<GHPullRequestReviewThread>>>(disposable).apply {
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

  private fun doLoadReviewThreads(model: GHCompletableFutureLoadingModel<List<DiffMappedValue<GHPullRequestReviewThread>>>) {
    model.future = reviewDataProvider.loadReviewThreads().thenApplyAsync(Function {
      it.mapNotNull(::mapThread)
    }, ProcessIOExecutorService.INSTANCE)
  }

  private fun mapThread(thread: GHPullRequestReviewThread): DiffMappedValue<GHPullRequestReviewThread>? {
    val originalCommitSha = thread.originalCommit?.oid ?: return null
    if (!diffData.contains(originalCommitSha, thread.path)) return null

    val location = when (diffData) {
      is GitTextFilePatchWithHistory.Cumulative -> {
        DiffLineLocation(thread.side, thread.line - 1)
      }
      is GitTextFilePatchWithHistory.Commit -> {
        diffData.mapPosition(originalCommitSha, thread.side, thread.originalLine - 1) ?: return null
      }
    }

    return DiffMappedValue(location, thread)
  }

  private fun updateReviewThreads() {
    val loadingModel = reviewThreadsLoadingModel ?: return
    if (loadingModel.loading) return
    reviewThreadsModel.value = if (showReviewThreads) loadingModel.result?.filter { showResolvedReviewThreads || !it.value.isResolved } else null
  }
}
