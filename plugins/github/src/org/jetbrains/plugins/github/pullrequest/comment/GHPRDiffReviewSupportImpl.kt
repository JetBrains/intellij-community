// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import git4idea.changes.GitTextFilePatchWithHistory
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRDiffEditorReviewComponentsFactoryImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewProcessModelImpl
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRSimpleOnesideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRTwosideDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRUnifiedDiffViewerReviewThreadsHandler
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHSimpleLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.util.function.Function
import kotlin.properties.Delegates.observable

class GHPRDiffReviewSupportImpl(private val project: Project,
                                private val reviewDataProvider: GHPRReviewDataProvider,
                                private val detailsDataProvider: GHPRDetailsDataProvider,
                                private val htmlImageLoader: AsyncHtmlImageLoader,
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

  override var discussionsViewOption by observable(DiscussionsViewOption.UNRESOLVED_ONLY) { _, _, _ ->
    updateReviewThreads()
  }

  override fun install(viewer: DiffViewerBase) {
    val diffRangesModel = SingleValueModel(if (reviewDataProvider.canComment()) diffData.patch.ranges else null)

    if (reviewDataProvider.canComment()) {
      loadPendingReview(viewer)
      var rangesInstalled = false
      reviewProcessModel.addAndInvokeChangesListener {
        if (reviewProcessModel.isActual && !rangesInstalled) {
          diffRangesModel.value = diffData.patch.ranges
          rangesInstalled = true
        }
      }
    }

    loadReviewThreads(viewer)

    val suggestedChangesHelper = GHPRSuggestedChangeHelper(project,
                                                           viewer, repositoryDataService.remoteCoordinates.repository,
                                                           reviewDataProvider,
                                                           detailsDataProvider)
    val componentsFactory = GHPRDiffEditorReviewComponentsFactoryImpl(project,
                                                                      reviewDataProvider,
                                                                      htmlImageLoader, avatarIconsProvider,
                                                                      diffData, suggestedChangesHelper,
                                                                      ghostUser,
                                                                      currentUser)
    when (viewer) {
      is SimpleOnesideDiffViewer ->
        GHPRSimpleOnesideDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory,
                                                        diffData.isCumulative)
      is UnifiedDiffViewer ->
        GHPRUnifiedDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory,
                                                  diffData.isCumulative)
      is TwosideTextDiffViewer ->
        GHPRTwosideDiffViewerReviewThreadsHandler(reviewProcessModel, diffRangesModel, reviewThreadsModel, viewer, componentsFactory,
                                                  diffData.isCumulative)
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
    if (thread.line == null && thread.originalLine == null) return null

    val mappedLocation = if (thread.line != null) {
      val commitSha = thread.commit?.oid ?: return null
      if (!diffData.contains(commitSha, thread.path)) return null
      when (thread.side) {
        Side.RIGHT -> {
          diffData.mapLine(commitSha, thread.line - 1, Side.RIGHT)
        }
        Side.LEFT -> {
          diffData.fileHistory.findStartCommit()?.let { baseSha ->
            diffData.mapLine(baseSha, thread.line - 1, Side.LEFT)
          }
        }
      }
    }
    else if (thread.originalLine != null) {
      val originalCommitSha = thread.originalCommit?.oid ?: return null
      if (!diffData.contains(originalCommitSha, thread.path)) return null
      when (thread.side) {
        Side.RIGHT -> {
          diffData.mapLine(originalCommitSha, thread.originalLine - 1, Side.RIGHT)
        }
        Side.LEFT -> {
          diffData.fileHistory.findFirstParent(originalCommitSha)?.let { parentSha ->
            diffData.mapLine(parentSha, thread.originalLine - 1, Side.LEFT)
          }
        }
      }
    }
    else {
      null
    }
    if (mappedLocation == null) return null

    return DiffMappedValue(mappedLocation, thread)
  }

  private fun updateReviewThreads() {
    val loadingModel = reviewThreadsLoadingModel ?: return
    if (loadingModel.loading) return
    reviewThreadsModel.value = when (discussionsViewOption) {
      DiscussionsViewOption.ALL -> loadingModel.result
      DiscussionsViewOption.UNRESOLVED_ONLY -> loadingModel.result?.filter { !it.value.isResolved }
      DiscussionsViewOption.DONT_SHOW -> null
    }
  }
}

val TextFilePatch.ranges: List<Range>
  get() = hunks.map(PatchHunkUtil::getRange)
