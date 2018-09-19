// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsUISettings
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsChangesLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDetailsLoader

class GithubPullRequestPreviewComponent(project: Project,
                                        detailsLoader: GithubPullRequestsDetailsLoader,
                                        changesLoader: GithubPullRequestsChangesLoader,
                                        actionManager: ActionManager,
                                        private val uiSettings: GithubPullRequestsUISettings)
  : Wrapper(), Disposable, GithubPullRequestsUISettings.SettingsChangedListener {

  private val splitter = OnePixelSplitter(true, "Github.PullRequest.Preview.Component", 0.7f)

  private val changes = GithubPullRequestChangesComponent(project, changesLoader, actionManager)
  private val details = GithubPullRequestDetailsComponent(project, detailsLoader)

  val toolbarComponent = changes.toolbarComponent

  init {
    Disposer.register(this, changes)
    Disposer.register(this, details)

    splitter.firstComponent = changes
    setContent(splitter)
    uiSettings.addChangesListener(this, this)
    updateDetails()
  }

  override fun settingsChanged() {
    updateDetails()
  }

  private fun updateDetails() {
    splitter.secondComponent = if (uiSettings.showDetails) details else null
  }

  override fun dispose() {}
}
