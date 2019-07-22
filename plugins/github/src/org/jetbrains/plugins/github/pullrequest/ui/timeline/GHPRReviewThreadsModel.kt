// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.util.handleOnEdt

class GHPRReviewThreadsModel(private val dataProvider: GithubPullRequestDataProvider) : Disposable {

  private val map = mutableMapOf<String, MutableList<GHPullRequestReviewThread>>()

  init {
    dataProvider.addRequestsChangesListener(this, object : GithubPullRequestDataProvider.RequestsChangedListener {
      override fun reviewThreadsRequestChanged() {
        handleUpdate()
      }
    })
    handleUpdate()
  }

  private fun handleUpdate() {
    dataProvider.reviewThreadsRequest.handleOnEdt(this) { threads, _ ->
      if (threads != null) {
        for (thread in threads) {
          map.getOrPut(thread.reviewId, { mutableListOf() }).add(thread)
        }
      }
    }
  }

  fun getThreads(id: String): List<GHPullRequestReviewThread> = map[id].orEmpty()

  override fun dispose() {}
}