// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider

internal class GithubPullRequestPreviewComponent(private val changes: GithubPullRequestChangesComponent,
                                                 private val details: GithubPullRequestDetailsComponent)
  : OnePixelSplitter("Github.PullRequest.Preview.Component", 0.5f), Disposable {

  private var currentProvider: GithubPullRequestDataProvider? = null

  private val requestChangesListener = object : GithubPullRequestDataProvider.RequestsChangedListener {
    override fun detailsRequestChanged() {
      details.loadAndShow(currentProvider!!.detailsRequest)
    }

    override fun commitsRequestChanged() {
      changes.loadAndShow(currentProvider!!.logCommitsRequest)
    }
  }

  init {
    firstComponent = details
    secondComponent = changes
  }

  fun setPreviewDataProvider(provider: GithubPullRequestDataProvider?) {
    val previousNumber = currentProvider?.number
    currentProvider?.removeRequestsChangesListener(requestChangesListener)
    currentProvider = provider
    currentProvider?.addRequestsChangesListener(requestChangesListener)

    if (previousNumber != provider?.number) {
      details.reset()
      changes.reset()
    }

    details.loadAndShow(provider?.detailsRequest)
    changes.loadAndShow(provider?.logCommitsRequest)
  }

  override fun dispose() {}
}
