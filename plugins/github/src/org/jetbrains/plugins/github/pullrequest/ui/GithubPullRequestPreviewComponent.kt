// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider

internal class GithubPullRequestPreviewComponent(private val changes: GithubPullRequestChangesComponent,
                                                 private val details: GithubPullRequestDetailsComponent)
  : OnePixelSplitter("Github.PullRequest.Preview.Component", 0.5f), Disposable {

  init {
    firstComponent = details
    secondComponent = changes
  }

  fun setPreviewDataProvider(provider: GithubPullRequestDataProvider?) {
    changes.loadAndShow(provider?.changesRequest)
    details.loadAndShow(provider?.detailsRequest)
  }

  override fun dispose() {}
}
