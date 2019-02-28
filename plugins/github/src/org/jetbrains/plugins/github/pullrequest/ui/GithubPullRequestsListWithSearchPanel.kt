// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsListLoader
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchQueryHolder
import java.awt.Component
import javax.swing.ListModel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent

internal class GithubPullRequestsListWithSearchPanel(project: Project,
                                                     copyPasteManager: CopyPasteManager,
                                                     actionManager: ActionManager,
                                                     autoPopupController: AutoPopupController,
                                                     avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                                                     private val loader: GithubPullRequestsListLoader,
                                                     private val listModel: ListModel<GithubSearchedIssue>,
                                                     private val searchQueryHolder: GithubPullRequestSearchQueryHolder,
                                                     private val listSelectionHolder: GithubPullRequestsListSelectionHolder)
  : BorderLayoutPanel(), Disposable {

  private val list = GithubPullRequestsList(copyPasteManager, avatarIconsProviderFactory, listModel)
  private val scrollPane = ScrollPaneFactory.createScrollPane(list,
                                                              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
    border = JBUI.Borders.empty()
    verticalScrollBar.model.addChangeListener { potentiallyLoadMore() }
  }
  private val infoPanel = HtmlInfoPanel()
  private val progressStripe = ProgressStripe(simplePanel(scrollPane).addToTop(infoPanel), this,
                                              ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)

  private val search = GithubPullRequestSearchPanel(project, autoPopupController, searchQueryHolder).apply {
    border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
  }

  private var autoSelectionNumber: Long? = null

  init {
    loader.addLoadingStateChangeListener(this) {
      updateProgress()
      updateEmptyText()
    }

    loader.addErrorChangeListener(this) {
      updateInfoPanel()
      updateEmptyText()
    }

    loader.addOutdatedStateChangeListener(this) {
      updateInfoPanel()
    }

    listModel.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        if (e.type == ListDataEvent.INTERVAL_ADDED)
          (e.index0..e.index1).find { listModel.getElementAt(it).number == autoSelectionNumber }
            ?.run { ApplicationManager.getApplication().invokeLater { ScrollingUtil.selectItem(list, this) } }
      }

      override fun contentsChanged(e: ListDataEvent) {}
      override fun intervalRemoved(e: ListDataEvent) {
        if (e.type == ListDataEvent.INTERVAL_REMOVED) autoSelectionNumber = listSelectionHolder.selectionNumber
      }
    })

    list.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
      if (!e.valueIsAdjusting) {
        val selectedIndex = list.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < listModel.size) {
          listSelectionHolder.selectionNumber = listModel.getElementAt(selectedIndex).number
          autoSelectionNumber = null
        }
      }
    }

    val popupHandler = object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        if (ListUtil.isPointOnSelection(list, x, y)) {
          val popupMenu = actionManager
            .createActionPopupMenu("GithubPullRequestListPopup",
                                   actionManager.getAction("Github.PullRequest.ToolWindow.List.Popup") as ActionGroup)
          popupMenu.setTargetComponent(list)
          popupMenu.component.show(comp, x, y)
        }
      }
    }
    list.addMouseListener(popupHandler)

    addToTop(search)
    addToCenter(progressStripe)

    resetSearch()
    updateProgress()
    updateInfoPanel()
    updateEmptyText()

    Disposer.register(this, list)
  }

  private fun updateProgress() {
    if (loader.loading) {
      progressStripe.startLoading()
    }
    else {
      progressStripe.stopLoading()
      scrollPane.viewport.validate()
      if (loader.error == null) potentiallyLoadMore()
    }
  }

  private fun updateEmptyText() {
    list.emptyText.clear()
    if (loader.loading) {
      list.emptyText.text = "Loading pull requests..."
    }
    else {
      val error = loader.error
      if (error != null) {
        list.emptyText.appendText(getErrorPrefix(), SimpleTextAttributes.ERROR_ATTRIBUTES)
          .appendSecondaryText(getLoadingErrorText(error), SimpleTextAttributes.ERROR_ATTRIBUTES, null)
          .appendSecondaryText("  ", SimpleTextAttributes.ERROR_ATTRIBUTES, null)
          .appendSecondaryText("Retry", SimpleTextAttributes.LINK_ATTRIBUTES) { loader.reset() }
      }
      else {
        if (searchQueryHolder.searchQuery.isEmpty()) {
          list.emptyText.text = "No pull requests loaded. "
          list.emptyText.appendSecondaryText("Refresh", SimpleTextAttributes.LINK_ATTRIBUTES) {
            loader.reset()
          }
        }
        else {
          list.emptyText.text = "No pull requests matching filters. "
          list.emptyText.appendSecondaryText("Reset Filters", SimpleTextAttributes.LINK_ATTRIBUTES) {
            resetSearch()
          }
        }
      }
    }
  }

  private fun updateInfoPanel() {
    val error = loader.error
    if (error != null && listModel.size != 0) {
      infoPanel.setInfo(
        "<html><body>${getErrorPrefix()}<br/>${getLoadingErrorText(error, "<br/>")}<a href=''>Retry</a></body></html>",
        HtmlInfoPanel.Severity.ERROR) { loader.reset() }
    }
    else if (loader.outdated) {
      infoPanel.setInfo("<html><body>The list is outdated. <a href=''>Refresh</a></body></html>",
                        HtmlInfoPanel.Severity.INFO) { loader.reset() }
    }
    else infoPanel.setInfo(null)
  }

  private fun getErrorPrefix() = if (listModel.size == 0) "Can't load pull requests." else "Can't load full pull requests list."

  private fun potentiallyLoadMore() {
    if (loader.canLoadMore() && isScrollAtThreshold()) {
      loader.loadMore()
    }
  }

  private fun isScrollAtThreshold(): Boolean {
    val verticalScrollBar = scrollPane.verticalScrollBar
    val visibleAmount = verticalScrollBar.visibleAmount
    val value = verticalScrollBar.value
    val maximum = verticalScrollBar.maximum
    if (maximum == 0) return false
    val scrollFraction = (visibleAmount + value) / maximum.toFloat()
    if (scrollFraction < 0.5) return false
    return true
  }

  private fun resetSearch() {
    search.searchText = "state:open"
  }

  override fun dispose() {}

  companion object {
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
  }
}