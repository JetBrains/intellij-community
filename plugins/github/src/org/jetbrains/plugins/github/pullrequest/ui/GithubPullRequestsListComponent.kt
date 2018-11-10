// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsLoader
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchComponent
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchModel
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.ProgressStripeProgressIndicator
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.Component
import javax.swing.JScrollBar
import javax.swing.ScrollPaneConstants
import javax.swing.event.ListSelectionEvent

internal class GithubPullRequestsListComponent(project: Project,
                                               copyPasteManager: CopyPasteManager,
                                               actionManager: ActionManager,
                                               autoPopupController: AutoPopupController,
                                               private val loader: GithubPullRequestsLoader,
                                               avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory)
  : BorderLayoutPanel(), Disposable, DataProvider {

  val selectionModel = GithubPullRequestsListSelectionModel()
  private val listModel = CollectionListModel<GithubSearchedIssue>()
  private val list = GithubPullRequestsList(copyPasteManager, avatarIconsProviderFactory, listModel)
  private val scrollPane = ScrollPaneFactory.createScrollPane(list,
                                                              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
    border = JBUI.Borders.empty()
    verticalScrollBar.model.addChangeListener { potentiallyLoadMore() }
  }
  private var loadOnScrollThreshold = true
  private var isDisposed = false
  private val errorPanel = HtmlErrorPanel()
  private val progressStripe = ProgressStripe(scrollPane, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
  private var progressIndicator = ProgressStripeProgressIndicator(progressStripe, true)

  private val searchModel = GithubPullRequestSearchModel()
  private val search = GithubPullRequestSearchComponent(project, autoPopupController, searchModel).apply {
    border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
  }

  init {
    searchModel.addListener(object : GithubPullRequestSearchModel.StateListener {
      override fun queryChanged() {
        loader.setSearchQuery(searchModel.query)
        refresh()
      }
    }, this)

    list.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
      if (!e.valueIsAdjusting) {
        if (list.selectedIndex < 0) selectionModel.current = null
        else selectionModel.current = listModel.getElementAt(list.selectedIndex)
      }
    }

    val popupHandler = object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        if (ListUtil.isPointOnSelection(list, x, y)) {
          val popupMenu = actionManager
            .createActionPopupMenu("GithubPullRequestListPopup",
                                   actionManager.getAction("Github.PullRequest.ToolWindow.List.Popup") as ActionGroup)
          popupMenu.setTargetComponent(this@GithubPullRequestsListComponent)
          popupMenu.component.show(comp, x, y)
        }
      }
    }
    list.addMouseListener(popupHandler)

    val tableWithError = JBUI.Panels
      .simplePanel(progressStripe)
      .addToTop(errorPanel)

    addToTop(search)
    addToCenter(tableWithError)

    resetSearch()

    Disposer.register(this, list)
  }

  override fun getData(dataId: String): Any? {
    return if (GithubPullRequestKeys.SELECTED_PULL_REQUEST.`is`(dataId)) selectionModel.current else null
  }

  @CalledInAwt
  fun refresh() {
    loadOnScrollThreshold = false
    list.selectionModel.clearSelection()
    listModel.removeAll()
    progressIndicator.cancel()
    progressIndicator = ProgressStripeProgressIndicator(progressStripe, true)
    loader.reset()
    loadMore()
  }

  private fun potentiallyLoadMore() {
    if (loadOnScrollThreshold && isScrollAtThreshold(scrollPane.verticalScrollBar)) {
      loadMore()
    }
  }

  private fun loadMore() {
    if (isDisposed) return
    loadOnScrollThreshold = false
    errorPanel.setError(null)

    list.emptyText.text = "Loading pull requests..."
    val indicator = progressIndicator
    loader.requestLoadMore(indicator).handleOnEdt { responsePage, error ->
      if (indicator.isCanceled) return@handleOnEdt
      when {
        error != null && !GithubAsyncUtil.isCancellation(error) -> {
          loadingErrorOccurred(error)
        }
        responsePage != null -> {
          moreDataLoaded(responsePage.items, responsePage.hasNext)
        }
      }
    }
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

  private fun moreDataLoaded(data: List<GithubSearchedIssue>, hasNext: Boolean) {
    if (searchModel.query.isEmpty()) {
      list.emptyText.text = "No pull requests loaded."
    }
    else {
      list.emptyText.text = "No pull requests matching filters."
      list.emptyText.appendSecondaryText("Reset Filters", SimpleTextAttributes.LINK_ATTRIBUTES) {
        resetSearch()
      }
    }
    loadOnScrollThreshold = hasNext
    listModel.add(data)

    //otherwise scrollbar will have old values (before data insert)
    scrollPane.viewport.validate()
    potentiallyLoadMore()
  }

  private fun resetSearch() {
    search.searchText = "state:open"
  }

  private fun loadingErrorOccurred(error: Throwable) {
    loadOnScrollThreshold = false
    val prefix = if (list.isEmpty) "Cannot load pull requests." else "Cannot load full pull requests list."
    list.emptyText.clear().appendText(prefix, SimpleTextAttributes.ERROR_ATTRIBUTES)
      .appendSecondaryText(getLoadingErrorText(error), SimpleTextAttributes.ERROR_ATTRIBUTES, null)
      .appendSecondaryText("  ", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
      .appendSecondaryText("Retry", SimpleTextAttributes.LINK_ATTRIBUTES) { refresh() }
    if (!list.isEmpty) {
      //language=HTML
      val errorText = "<html><body>$prefix<br/>${getLoadingErrorText(error, "<br/>")}<a href=''>Retry</a></body></html>"
      errorPanel.setError(errorText, linkActivationListener = { refresh() })
    }
  }

  private fun getLoadingErrorText(error: Throwable, newLineSeparator: String = "\n"): String {
    if (error is GithubStatusCodeException && error.error != null) {
      val githubError = error.error!!
      val builder = StringBuilder(githubError.message)
      if (githubError.errors.isNotEmpty()) {
        builder.append(": ").append(newLineSeparator)
        for (e in githubError.errors) {
          builder.append(e.message ?: "${e.code} error in ${e.resource} field ${e.field}").append(newLineSeparator)
        }
      }
      return builder.toString()
    }

    return error.message?.let { addDotIfNeeded(it) } ?: "Unknown loading error."
  }

  private fun addDotIfNeeded(line: String) = if (line.endsWith('.')) line else "$line."

  override fun dispose() {
    isDisposed = true
  }
}