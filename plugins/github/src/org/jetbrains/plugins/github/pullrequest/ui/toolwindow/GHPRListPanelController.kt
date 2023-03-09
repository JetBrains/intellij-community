// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRListPanelController(
  project: Project,
  scope: CoroutineScope,
  account: GithubAccount,
  private val listLoader: GHListLoader<*>,
  private val searchVm: GHPRSearchPanelViewModel,
  private val repository: String,
  private val emptyText: StatusText,
  private val listComponent: JComponent,
  private val mainPanel: JPanel,
  listenersDisposable: Disposable
) {
  private val loadingState: MutableStateFlow<Boolean> = MutableStateFlow(listLoader.loading)
  private val errorState: MutableStateFlow<Throwable?> = MutableStateFlow(listLoader.error)

  private val errorPanel: JComponent = createErrorPanel(project, scope, account)

  init {
    listLoader.addLoadingStateChangeListener(listenersDisposable) {
      loadingState.value = listLoader.loading
    }
    listLoader.addErrorChangeListener(listenersDisposable) {
      errorState.value = listLoader.error
    }

    scope.launch {
      errorState.collect { error ->
        update(error)
      }
    }
    scope.launch {
      combineAndCollect(loadingState, searchVm.searchState) { isLoading, searchValue ->
        updateEmptyText(isLoading, searchValue)
      }
    }
  }

  private fun update(error: Throwable?) {
    mainPanel.removeAll()
    val visibleComponent = if (error != null) errorPanel else listComponent
    mainPanel.add(visibleComponent, BorderLayout.CENTER)
    mainPanel.revalidate()
    mainPanel.repaint()
  }

  private fun updateEmptyText(isLoading: Boolean, searchValue: GHPRListSearchValue) {
    emptyText.clear()
    if (isLoading) {
      emptyText.appendText(CollaborationToolsBundle.message("review.list.empty.state.loading"))
      return
    }

    if (searchValue.filterCount == 0) {
      emptyText.appendText(GithubBundle.message("pull.request.list.nothing.loaded", repository))
    }
    else {
      emptyText
        .appendText(GithubBundle.message("pull.request.list.no.matches"))
        .appendSecondaryText(GithubBundle.message("pull.request.list.filters.clear"), SimpleTextAttributes.LINK_ATTRIBUTES) {
          searchVm.searchState.value = GHPRListSearchValue.EMPTY
        }
    }
  }

  private fun createErrorPanel(project: Project, scope: CoroutineScope, account: GithubAccount): JComponent {
    val errorPresenter = GHPRErrorStatusPresenter(project, account) {
      listLoader.reset()
      listLoader.loadMore()
    }
    val errorPanel = ErrorStatusPanelFactory.create(scope, errorState, errorPresenter)
    return JPanel(SingleComponentCenteringLayout()).apply {
      add(errorPanel)
    }
  }
}