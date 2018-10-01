// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsUISettings

class GithubPullRequestPreviewComponent(private val uiSettings: GithubPullRequestsUISettings,
                                        changes: GithubPullRequestChangesComponent,
                                        private val details: GithubPullRequestDetailsComponent)
  : OnePixelSplitter(true, "Github.PullRequest.Preview.Component", 0.6f),
    Disposable, GithubPullRequestsUISettings.SettingsChangedListener {

  val toolbarComponent = changes.toolbarComponent

  init {
    Disposer.register(this, changes)
    Disposer.register(this, details)

    firstComponent = changes
    uiSettings.addChangesListener(this, this)
    updateDetails()
  }

  override fun settingsChanged() {
    updateDetails()
  }

  private fun updateDetails() {
    secondComponent = if (uiSettings.showDetails) details else null
  }

  override fun dispose() {}
}
