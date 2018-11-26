// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import java.awt.BorderLayout

internal class GithubPullRequestDetailsComponent(iconProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : GithubDataLoadingComponent<GithubPullRequestDetailedWithHtml>(), Disposable {
  private val detailsPanel = GithubPullRequestDetailsPanel(iconProviderFactory)
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
    isOpaque = false
  }

  init {
    isOpaque = true

    detailsPanel.emptyText.text = DEFAULT_EMPTY_TEXT
    loadingPanel.add(detailsPanel)
    setContent(loadingPanel)
    Disposer.register(this, detailsPanel)
  }

  override fun reset() {
    detailsPanel.emptyText.text = DEFAULT_EMPTY_TEXT
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

  override fun setBusy(busy: Boolean) {
    if (busy) {
      detailsPanel.emptyText.clear()
      loadingPanel.startLoading()
    }
    else {
      loadingPanel.stopLoading()
    }
  }

  override fun updateUI() {
    super.updateUI()
    background = UIUtil.getListBackground()
  }

  override fun dispose() {}

  companion object {
    //language=HTML
    private const val DEFAULT_EMPTY_TEXT = "Select pull request to view details"
  }
}
