// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.list

import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener
import com.intellij.vcs.log.ui.frame.ProgressStripe
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRListUpdatesChecker
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListPersistentSearchHistory
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchHistoryModel
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchPanelViewModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.ChangeEvent

internal class GHPRListPanelFactory(private val project: Project,
                                    private val repositoryDataService: GHPRRepositoryDataService,
                                    private val securityService: GHPRSecurityService,
                                    private val listLoader: GHPRListLoader,
                                    private val listUpdatesChecker: GHPRListUpdatesChecker,
                                    private val account: GithubAccount,
                                    private val disposable: Disposable) {

  private val scope = MainScope().also { Disposer.register(disposable) { it.cancel() } }

  fun create(list: JBList<GHPullRequestShort>, avatarIconsProvider: GHAvatarIconsProvider): JComponent {

    val actionManager = ActionManager.getInstance()

    val historyModel = GHPRSearchHistoryModel(project.service<GHPRListPersistentSearchHistory>())
    val searchVm = GHPRSearchPanelViewModel(scope, repositoryDataService, historyModel, securityService.currentUser)
    scope.launch {
      searchVm.searchState.collectLatest {
        listLoader.searchQuery = it.toQuery()
      }
    }

    val searchPanel = GHPRSearchPanelFactory(searchVm, avatarIconsProvider).create(scope)

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

    val controlsPanel = VerticalListPanel().apply {
      add(searchPanel)
      add(outdatedStatePanel)
    }

    val listLoaderPanel = createListLoaderPanel(listLoader, list, disposable)
    val listWrapper = Wrapper()
    val progressStripe = ProgressStripe(
      listWrapper,
      disposable,
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    ).apply {
      if (listLoader.loading) startLoadingImmediately() else stopLoading()
    }
    ScrollableContentBorder.setup(listLoaderPanel, Side.TOP, progressStripe)
    listLoader.addLoadingStateChangeListener(disposable) {
      if (listLoader.loading) progressStripe.startLoading() else progressStripe.stopLoading()
    }

    val repository = repositoryDataService.repositoryCoordinates.repositoryPath.repository
    GHPRListPanelController(
      project, scope,
      account, listLoader, searchVm, repository,
      list.emptyText, listLoaderPanel, listWrapper,
      disposable
    )

    return JBUI.Panels.simplePanel(progressStripe).addToTop(controlsPanel).andTransparent().also {
      val listController = GHPRListControllerImpl(repositoryDataService, listLoader)
      DataManager.registerDataProvider(it) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER.`is`(dataId) -> listController
          GHPRActionKeys.SELECTED_PULL_REQUEST.`is`(dataId) -> if (list.isSelectionEmpty) null else list.selectedValue
          else -> null
        }
      }
      actionManager.getAction("Github.PullRequest.List.Reload").registerCustomShortcutSet(it, disposable)
    }
  }

  private fun createListLoaderPanel(loader: GHListLoader<*>, list: JComponent, disposable: Disposable): JScrollPane {
    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

      val model = verticalScrollBar.model
      val listener = object : BoundedRangeModelThresholdListener(model, 0.7f) {
        override fun onThresholdReached() {
          if (loader.canLoadMore()) {
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

    return scrollPane
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