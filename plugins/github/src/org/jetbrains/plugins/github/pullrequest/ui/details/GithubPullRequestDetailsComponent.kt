// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.pullrequest.data.service.GithubPullRequestsStateService
import org.jetbrains.plugins.github.pullrequest.ui.GithubDataLoadingComponent
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings
import java.awt.BorderLayout

internal class GithubPullRequestDetailsComponent(sharedProjectSettings: GithubSharedProjectSettings,
                                                 private val dataLoader: GithubPullRequestsDataLoader,
                                                 stateService: GithubPullRequestsStateService,
                                                 iconProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                                 accountDetails: GithubAuthenticatedUser,
                                                 repoDetails: GithubRepoDetailed)
  : GithubDataLoadingComponent<GithubPullRequestDetailedWithHtml>(), Disposable {
  private val detailsPanel = GithubPullRequestDetailsPanel(sharedProjectSettings, stateService, iconProviderFactory,
                                                           accountDetails, repoDetails)
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
    isOpaque = false
  }
  private val backgroundLoadingPanel = ProgressStripe(loadingPanel, this,
                                                      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
    isOpaque = false
  }

  init {
    isOpaque = true

    detailsPanel.emptyText.text = DEFAULT_EMPTY_TEXT
    loadingPanel.add(detailsPanel)
    setContent(backgroundLoadingPanel)
    Disposer.register(this, detailsPanel)
  }

  override fun reset() {
    detailsPanel.emptyText.text = DEFAULT_EMPTY_TEXT
    detailsPanel.details = null
  }

  override fun handleResult(result: GithubPullRequestDetailedWithHtml) {
    detailsPanel.details = result
    if (!result.merged && result.state == GithubIssueState.open && result.mergeable == null) {
      ApplicationManager.getApplication().invokeLater {
        dataLoader.reloadDetails(result.number)
      }
    }
  }

  override fun handleError(error: Throwable) {
    detailsPanel.emptyText
      .clear()
      .appendText("Can't load details", SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
  }

  override fun setBusy(busy: Boolean) {
    if (busy) {
      if (detailsPanel.details == null) {
        detailsPanel.emptyText.clear()
        loadingPanel.startLoading()
      }
      else {
        backgroundLoadingPanel.startLoading()
      }
    }
    else {
      loadingPanel.stopLoading()
      backgroundLoadingPanel.stopLoading()
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
