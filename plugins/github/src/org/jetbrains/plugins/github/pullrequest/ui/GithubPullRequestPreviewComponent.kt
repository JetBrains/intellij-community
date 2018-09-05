// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsChangesLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDetailsLoader

class GithubPullRequestPreviewComponent(project: Project,
                                        detailsLoader: GithubPullRequestsDetailsLoader,
                                        changesLoader: GithubPullRequestsChangesLoader)
  : Wrapper(), Disposable {

  private val splitter = OnePixelSplitter(true, "Github.PullRequest.Preview.Component", 0.7f)

  private val changes = GithubPullRequestChangesComponent(project, changesLoader)
  private val details = GithubPullRequestDetailsComponent(project, detailsLoader)

  val toolbarComponent = changes.toolbarComponent

  init {
    Disposer.register(this, changes)
    Disposer.register(this, details)

    splitter.firstComponent = changes
    splitter.secondComponent = details
    setContent(splitter)
  }

  override fun dispose() {}
}
