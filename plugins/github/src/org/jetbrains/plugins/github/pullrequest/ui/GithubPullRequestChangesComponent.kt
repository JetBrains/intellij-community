// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.vcs.log.ui.frame.ProgressStripe
import git4idea.GitCommit
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewThreadsProviderImpl
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPREditorReviewThreadComponentFactory
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesBrowser
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.BorderLayout

internal class GithubPullRequestChangesComponent(project: Project,
                                                 projectUiSettings: GithubPullRequestsProjectUISettings,
                                                 private val diffCommentComponentFactory: GHPREditorReviewThreadComponentFactory)
  : GithubDataLoadingComponent<List<GitCommit>>(), Disposable {

  private val changesModel = SingleValueModel<List<GitCommit>?>(null)
  private val changesBrowser = GHPRChangesBrowser(changesModel, project, projectUiSettings).apply {
    diffReviewThreadsProvider = GHPRDiffReviewThreadsProviderImpl(dataProvider!!, diffCommentComponentFactory)
  }
  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
  private val backgroundLoadingPanel = ProgressStripe(loadingPanel, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  init {
    loadingPanel.add(changesBrowser, BorderLayout.CENTER)
    changesBrowser.emptyText.text = DEFAULT_EMPTY_TEXT
    setContent(loadingPanel)
    Disposer.register(this, changesBrowser)
  }

  override fun extractRequest(provider: GithubPullRequestDataProvider) = provider.logCommitsRequest

  override fun resetUI() {
    changesBrowser.emptyText.text = DEFAULT_EMPTY_TEXT
    changesModel.value = null
  }

  override fun handleResult(result: List<GitCommit>) {
    changesBrowser.emptyText.text = "Pull request does not contain any changes"
    changesModel.value = result
  }

  override fun handleError(error: Throwable) {
    changesBrowser.emptyText
      .clear()
      .appendText("Can't load changes", SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
  }

  override fun setBusy(busy: Boolean) {
    if (busy) {
      if (changesModel.value.isNullOrEmpty()) {
        changesBrowser.emptyText.clear()
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

  override fun dispose() {}

  companion object {
    //language=HTML
    private const val DEFAULT_EMPTY_TEXT = "Select pull request to view list of changed files"
  }
}