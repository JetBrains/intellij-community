// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.GithubPullRequestDetailsComponent

internal class GithubPullRequestPreviewComponent(private val changes: GithubPullRequestChangesComponent,
                                                 private val details: GithubPullRequestDetailsComponent)
  : OnePixelSplitter("Github.PullRequest.Preview.Component", 0.5f), Disposable {

  private var currentProvider: GithubPullRequestDataProvider? = null

  private val requestChangesListener = object : GithubPullRequestDataProvider.RequestsChangedListener {
    override fun detailsRequestChanged() {
      details.loadAndUpdate(currentProvider!!.detailsRequest)
    }

    override fun commitsRequestChanged() {
      changes.loadAndUpdate(currentProvider!!.logCommitsRequest)
    }
  }

  init {
    firstComponent = details
    secondComponent = changes
  }

  fun setPreviewDataProvider(provider: GithubPullRequestDataProvider?) {
    currentProvider?.removeRequestsChangesListener(requestChangesListener)
    currentProvider = provider
    currentProvider?.addRequestsChangesListener(requestChangesListener)

    changes.loadAndShow(provider?.logCommitsRequest)
    details.loadAndShow(provider?.detailsRequest)
  }

  override fun dispose() {}
}
