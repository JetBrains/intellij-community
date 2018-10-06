// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import java.awt.BorderLayout

internal class GithubPullRequestDetailsComponent(project: Project) : GithubDataLoadingComponent<GithubPullRequestDetailedWithHtml>(), Disposable {
  private val detailsPanel = GithubPullRequestDetailsPanel(project)
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  init {
    loadingPanel.add(detailsPanel)
    setContent(loadingPanel)
  }

  override fun reset() {
    detailsPanel.emptyText.clear()
    detailsPanel.details = null
  }

  override fun handleResult(result: GithubPullRequestDetailedWithHtml) {
    detailsPanel.details = result
  }

  override fun handleError(error: Throwable) {
    detailsPanel.emptyText
      .clear()
      .appendText("Cannot load details", SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
  }

  override fun setBusy(busy: Boolean) = if (busy) loadingPanel.startLoading() else loadingPanel.stopLoading()

  override fun dispose() {}
}
