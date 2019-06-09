// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import kotlin.properties.Delegates

internal class GithubPullRequestPreviewComponent(private val changes: GithubPullRequestChangesComponent,
                                                 private val details: GithubPullRequestDetailsComponent)
  : OnePixelSplitter("Github.PullRequest.Preview.Component", 0.5f), Disposable {

  @get:CalledInAwt
  @set:CalledInAwt
  var dataProvider by Delegates.observable<GithubPullRequestDataProvider?>(null) { _, oldValue, newValue ->
    oldValue?.removeRequestsChangesListener(requestChangesListener)
    newValue?.addRequestsChangesListener(requestChangesListener)

    if (oldValue?.number != newValue?.number) {
      details.reset()
      changes.reset()
    }

    changes.dataProvider = newValue
    details.dataProvider = newValue
  }

  private val requestChangesListener: GithubPullRequestDataProvider.RequestsChangedListener = object : GithubPullRequestDataProvider.RequestsChangedListener {
    override fun detailsRequestChanged() {
      details.dataProvider = dataProvider
    }

    override fun commitsRequestChanged() {
      changes.dataProvider = dataProvider
    }
  }

  init {
    firstComponent = details
    secondComponent = changes
  }

  override fun dispose() {
    dataProvider?.removeRequestsChangesListener(requestChangesListener)
  }
}
