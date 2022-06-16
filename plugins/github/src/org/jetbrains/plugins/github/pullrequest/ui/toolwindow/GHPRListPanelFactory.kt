// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRListUpdatesChecker
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.search.GHPRSearchCompletionProvider
import org.jetbrains.plugins.github.pullrequest.search.GHPRSearchQueryHolder
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.ui.component.GHHandledErrorPanelModel
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ChangeEvent

internal class GHPRListPanelFactory(private val project: Project,
                                    private val repositoryDataService: GHPRRepositoryDataService,
                                    private val listLoader: GHListLoader<GHPullRequestShort>,
                                    private val searchQueryHolder: GHPRSearchQueryHolder,
                                    private val listUpdatesChecker: GHPRListUpdatesChecker,
                                    private val account: GithubAccount,
                                    private val disposable: Disposable) {

  fun create(list: JBList<GHPullRequestShort>): JComponent {

    val actionManager = ActionManager.getInstance()

    val searchStringModel = SingleValueModel(searchQueryHolder.queryString)
    searchQueryHolder.addQueryChangeListener(disposable) {
      if (searchStringModel.value != searchQueryHolder.queryString)
        searchStringModel.value = searchQueryHolder.queryString
    }
    searchStringModel.addListener {
      searchQueryHolder.queryString = searchStringModel.value
    }

    ListEmptyTextController(listLoader, searchQueryHolder, list.emptyText, disposable)

    val searchCompletionProvider = GHPRSearchCompletionProvider(project, repositoryDataService)
    val pullRequestUiSettings = GithubPullRequestsProjectUISettings.getInstance(project)
    val search = GHPRSearchPanel.create(project, searchStringModel, searchCompletionProvider, pullRequestUiSettings).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val outdatedStatePanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUIScale.scale(5), 0)).apply {
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(4, 0)
      add(JLabel(GithubBundle.message("pull.request.list.outdated")))
      add(ActionLink(GithubBundle.message("pull.request.list.refresh")) {
        listLoader.reset()
      })

      isVisible = false
    }
    OutdatedPanelController(listLoader, listUpdatesChecker, outdatedStatePanel, disposable)

    val errorHandler = GHApiLoadingErrorHandler(project, account) {
      listLoader.reset()
    }
    val errorModel = GHHandledErrorPanelModel(GithubBundle.message("pull.request.list.cannot.load"), errorHandler).apply {
      error = listLoader.error
    }
    listLoader.addErrorChangeListener(disposable) {
      errorModel.error = listLoader.error
    }
    val errorPane = GHHtmlErrorPanel.create(errorModel)

    val controlsPanel = JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(search)
      add(outdatedStatePanel)
      add(errorPane)
    }
    val listLoaderPanel = createListLoaderPanel(listLoader, list, disposable)
    return JBUI.Panels.simplePanel(listLoaderPanel).addToTop(controlsPanel).andTransparent().also {
      DataManager.registerDataProvider(it) { dataId ->
        if (GHPRActionKeys.SELECTED_PULL_REQUEST.`is`(dataId)) {
          if (list.isSelectionEmpty) null else list.selectedValue
        }
        else null
      }
      actionManager.getAction("Github.PullRequest.List.Reload").registerCustomShortcutSet(it, disposable)
    }
  }

  private fun createListLoaderPanel(loader: GHListLoader<*>, list: JComponent, disposable: Disposable): JComponent {

    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

      val model = verticalScrollBar.model
      val listener = object : BoundedRangeModelThresholdListener(model, 0.7f) {
        override fun onThresholdReached() {
          if (!loader.loading && loader.canLoadMore()) {
            loader.loadMore()
          }
        }
      }
      model.addChangeListener(listener)
      loader.addLoadingStateChangeListener(disposable) {
        if (!loader.loading) listener.stateChanged(ChangeEvent(loader))
      }
    }
    loader.addDataListener(disposable, object : GHListLoader.ListDataListener {
      override fun onAllDataRemoved() {
        if (scrollPane.isShowing) loader.loadMore()
      }
    })
    val progressStripe = ProgressStripe(scrollPane, disposable,
                                        ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS).apply {
      if (loader.loading) startLoadingImmediately() else stopLoading()
    }
    loader.addLoadingStateChangeListener(disposable) {
      if (loader.loading) progressStripe.startLoading() else progressStripe.stopLoading()
    }
    return progressStripe
  }

  private class ListEmptyTextController(private val listLoader: GHListLoader<*>,
                                        private val searchHolder: GHPRSearchQueryHolder,
                                        private val emptyText: StatusText,
                                        listenersDisposable: Disposable) {
    init {
      listLoader.addLoadingStateChangeListener(listenersDisposable, ::update)
      searchHolder.addQueryChangeListener(listenersDisposable, ::update)
    }

    private fun update() {
      emptyText.clear()
      if (listLoader.loading || listLoader.error != null) return


      val query = searchHolder.query
      if (query == GHPRSearchQuery.DEFAULT) {
        emptyText.appendText(GithubBundle.message("pull.request.list.no.matches"))
          .appendSecondaryText(GithubBundle.message("pull.request.list.reset.filters"),
                               SimpleTextAttributes.LINK_ATTRIBUTES) {
            searchHolder.query = GHPRSearchQuery.EMPTY
          }
      }
      else if (query.isEmpty()) {
        emptyText.appendText(GithubBundle.message("pull.request.list.nothing.loaded"))
      }
      else {
        emptyText.appendText(GithubBundle.message("pull.request.list.no.matches"))
          .appendSecondaryText(GithubBundle.message("pull.request.list.reset.filters.to.default", GHPRSearchQuery.DEFAULT.toString()),
                               SimpleTextAttributes.LINK_ATTRIBUTES) {
            searchHolder.query = GHPRSearchQuery.DEFAULT
          }
      }
    }
  }

  private class OutdatedPanelController(private val listLoader: GHListLoader<*>,
                                        private val listChecker: GHPRListUpdatesChecker,
                                        private val panel: JPanel,
                                        listenersDisposable: Disposable) {
    init {
      listLoader.addLoadingStateChangeListener(listenersDisposable, ::update)
      listLoader.addErrorChangeListener(listenersDisposable, ::update)
      listChecker.addOutdatedStateChangeListener(listenersDisposable, ::update)
    }

    private fun update() {
      panel.isVisible = listChecker.outdated && (!listLoader.loading && listLoader.error == null)
    }
  }
}