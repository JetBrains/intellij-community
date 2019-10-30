// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread

class GHPRReviewsThreadsModelsProviderImpl : GHPRReviewsThreadsModelsProvider {
  private var threadsByReview = mapOf<String, List<GHPullRequestReviewThread>>()
  private val threadsModelsByReview = mutableMapOf<String, GHPRReviewThreadsModel>()

  override fun setReviewsThreads(threads: List<GHPullRequestReviewThread>) {
    threadsByReview = threads.groupBy { it.reviewId }
    for ((reviewId, model) in threadsModelsByReview) {
      model.update(threadsByReview[reviewId].orEmpty())
    }
  }

  override fun getReviewThreadsModel(reviewId: String): GHPRReviewThreadsModel {
    return threadsModelsByReview.getOrPut(reviewId) {
      GHPRReviewThreadsModel(threadsByReview[reviewId].orEmpty())
    }
  }
}