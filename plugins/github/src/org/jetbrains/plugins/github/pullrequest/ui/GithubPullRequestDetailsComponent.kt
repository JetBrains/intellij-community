// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDetailsLoader
import org.jetbrains.plugins.github.pullrequest.data.SingleWorkerProcessExecutor
import java.awt.BorderLayout

class GithubPullRequestDetailsComponent(project: Project, loader: GithubPullRequestsDetailsLoader)
  : Wrapper(), Disposable,
    SingleWorkerProcessExecutor.ProcessStateListener,
    GithubPullRequestsDetailsLoader.LoadingListener {
  private val detailsPanel = GithubPullRequestDetailsPanel(project)

  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  init {
    loader.addProcessListener(this, this)
    loader.addLoadingListener(this, this)

    loadingPanel.add(detailsPanel)
    setContent(loadingPanel)
  }

  override fun processStarted() {
    loadingPanel.startLoading()
    detailsPanel.details = null
    detailsPanel.emptyText.clear()
  }

  override fun processFinished() {
    loadingPanel.stopLoading()
  }

  override fun detailsLoaded(details: GithubPullRequestDetailedWithHtml) {
    detailsPanel.details = details
  }

  override fun errorOccurred(error: Throwable) {
    detailsPanel.details = null
    detailsPanel.emptyText.appendText("Cannot load details", SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
  }

  override fun loaderCleared() {
    detailsPanel.details = null
    detailsPanel.emptyText.clear()
  }

  override fun dispose() {}
}
