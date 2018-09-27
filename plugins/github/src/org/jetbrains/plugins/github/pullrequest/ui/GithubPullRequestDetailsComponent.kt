// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsDataLoader
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture

class GithubPullRequestDetailsComponent(project: Project,
                                        private val selectionModel: GithubPullRequestsListSelectionModel,
                                        private val dataLoader: GithubPullRequestsDataLoader)
  : Wrapper(), Disposable, GithubPullRequestsListSelectionModel.SelectionChangedListener {

  private val detailsPanel = GithubPullRequestDetailsPanel(project)

  private val loadingPanel = JBLoadingPanel(BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  private var updateFuture: CompletableFuture<Unit>? = null

  init {
    selectionModel.addChangesListener(this, this)

    loadingPanel.add(detailsPanel)
    setContent(loadingPanel)
  }

  override fun selectionChanged() {
    reset()
    updateFuture = updateDetails(selectionModel.current)
  }

  private fun updateDetails(item: GithubSearchedIssue?) =
    item?.let { selection ->
      loadingPanel.startLoading()

      dataLoader.getDataProvider(selection).detailsRequest
        .handleOnEdt { details, error ->
          when {
            error != null && !GithubAsyncUtil.isCancellation(error) -> {
              detailsPanel.emptyText
                .appendText("Cannot load details", SimpleTextAttributes.ERROR_ATTRIBUTES)
                .appendSecondaryText(error.message ?: "Unknown error", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
            }
            details != null -> {
              detailsPanel.details = details
            }
          }
          loadingPanel.stopLoading()
        }
    }

  private fun reset() {
    updateFuture?.cancel(true)
    detailsPanel.emptyText.clear()
    detailsPanel.details = null
  }

  override fun dispose() {}
}
