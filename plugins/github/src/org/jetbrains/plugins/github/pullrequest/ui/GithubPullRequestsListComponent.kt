// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsLoader
import javax.swing.JScrollBar
import javax.swing.ScrollPaneConstants

class GithubPullRequestsListComponent(private val loader: GithubPullRequestsLoader)
  : Wrapper(), Disposable, GithubPullRequestsLoader.StateListener {

  private val tableModel = GithubPullRequestsTableModel()
  private val table = GithubPullRequestsTable(tableModel)
  private val scrollPane = ScrollPaneFactory.createScrollPane(table,
                                                              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
    border = JBUI.Borders.empty()
    verticalScrollBar.model.addChangeListener { potentiallyLoadMore() }
  }
  private var loadOnScrollThreshold = true
  private val errorPanel = HtmlErrorPanel()
  private val progressStripe = ProgressStripe(scrollPane, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  init {
    loader.addStateListener(this)

    val tableWithError = JBUI.Panels.simplePanel(progressStripe).addToTop(errorPanel)
    setContent(tableWithError)
  }

  private fun potentiallyLoadMore() {
    if (loadOnScrollThreshold && isScrollAtThreshold(scrollPane.verticalScrollBar)) {
      loadMore()
    }
  }

  private fun loadMore() {
    loadOnScrollThreshold = false
    loader.requestLoadMore()
  }

  private fun isScrollAtThreshold(verticalScrollBar: JScrollBar): Boolean {
    val visibleAmount = verticalScrollBar.visibleAmount
    val value = verticalScrollBar.value
    val maximum = verticalScrollBar.maximum
    if (maximum == 0) return false
    val scrollFraction = (visibleAmount + value) / maximum.toFloat()
    if (scrollFraction < 0.5) return false
    return true
  }

  override fun loadingStarted() {
    invokeAndWaitIfNeed {
      table.emptyText.text = "Loading pull requests..."
      errorPanel.setError(null)
      progressStripe.startLoading()
    }
  }

  override fun loadingStopped() {
    invokeAndWaitIfNeed {
      progressStripe.stopLoading()
    }
  }

  override fun moreDataLoaded(data: List<GithubSearchedIssue>, hasNext: Boolean) {
    invokeAndWaitIfNeed {
      table.emptyText.text = "No pull requests loaded."
      loadOnScrollThreshold = hasNext
      tableModel.addItems(data)

      //otherwise scrollbar will have old values (before data insert)
      scrollPane.viewport.validate()
      potentiallyLoadMore()
    }
  }

  override fun loadingErrorOccurred(error: Throwable) {
    invokeAndWaitIfNeed {
      loadOnScrollThreshold = false
      val prefix = if (table.isEmpty) "Cannot load pull requests." else "Cannot load full pull requests list."
      table.emptyText.clear().appendText(prefix, SimpleTextAttributes.ERROR_ATTRIBUTES)
        .appendSecondaryText(getLoadingErrorText(error), SimpleTextAttributes.ERROR_ATTRIBUTES, null)
        .appendSecondaryText("Retry", SimpleTextAttributes.LINK_ATTRIBUTES) { loadMore() }
      if (!table.isEmpty) {
        //language=HTML
        val errorText = "<html><body>$prefix ${getLoadingErrorText(error)}<a href=''>Retry</a></body></html>"
        errorPanel.setError(errorText, linkActivationListener = { loadMore() })
      }
    }
  }

  private fun getLoadingErrorText(error: Throwable): String {
    return error.message?.let { addDotIfNeeded(it) }?.let { addSpaceIfNeeded(it) }
           ?: "Unknown loading error. "
  }

  private fun addDotIfNeeded(line: String) = if (line.endsWith('.')) line else "$line."
  private fun addSpaceIfNeeded(line: String) = if (line.endsWith(' ')) line else "$line "

  override fun dispose() {
    loader.removeStateListener(this)
  }
}