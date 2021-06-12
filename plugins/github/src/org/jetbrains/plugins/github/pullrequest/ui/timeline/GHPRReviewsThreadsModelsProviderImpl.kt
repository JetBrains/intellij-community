// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider

class GHPRReviewsThreadsModelsProviderImpl(private val reviewDataProvider: GHPRReviewDataProvider,
                                           private val parentDisposable: Disposable)
  : GHPRReviewsThreadsModelsProvider {

  private var threadsByReview = mapOf<String, List<GHPullRequestReviewThread>>()
  private var loading = false
  private val threadsModelsByReview = mutableMapOf<String, GHPRReviewThreadsModel>()
  private var threadsUpdateRequired = false

  init {
    reviewDataProvider.addReviewThreadsListener(parentDisposable) {
      if (threadsModelsByReview.isNotEmpty()) requestUpdateReviewsThreads()
    }
  }

  override fun getReviewThreadsModel(reviewId: String): GHPRReviewThreadsModel {
    return threadsModelsByReview.getOrPut(reviewId) {
      GHPRReviewThreadsModel()
    }.apply {
      val loadedThreads = threadsByReview[reviewId]
      threadsUpdateRequired = true
      if (loadedThreads == null && !loading) requestUpdateReviewsThreads()
      else update(loadedThreads.orEmpty())
    }
  }

  private fun updateReviewsThreads(threads: List<GHPullRequestReviewThread>) {
    val threadsMap = mutableMapOf<String, MutableList<GHPullRequestReviewThread>>()
    for (thread in threads) {
      val reviewId = thread.reviewId
      if (reviewId != null) {
        val list = threadsMap.getOrPut(reviewId) { mutableListOf() }
        list.add(thread)
      }
    }
    threadsByReview = threadsMap
    for ((reviewId, model) in threadsModelsByReview) {
      model.update(threadsByReview[reviewId].orEmpty())
    }
  }

  private fun requestUpdateReviewsThreads() {
    loading = true
    threadsUpdateRequired = false
    reviewDataProvider.loadReviewThreads().handleOnEdt(parentDisposable) { threads, _ ->
      if (threads != null) {
        updateReviewsThreads(threads)
        loading = false
        if (threadsUpdateRequired) requestUpdateReviewsThreads()
      }
      else {
        loading = false
      }
    }
  }
}