// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.list

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListSearchValue
import org.jetbrains.plugins.github.ui.component.GHHtmlErrorPanel
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRListPanelController(
  project: Project,
  scope: CoroutineScope,
  private val listVm: GHPRListViewModel,
  private val emptyText: StatusText,
  private val listComponent: JComponent,
  private val mainPanel: Wrapper
) {

  private val errorPanel: JComponent = createErrorPanel(project, scope, listVm.account)

  init {
    scope.launch {
      listVm.error.collect { error ->
        mainPanel.setContent(if (error != null) errorPanel else listComponent)
        mainPanel.repaint()
      }
    }
    scope.launch {
      combineAndCollect(listVm.isLoading, listVm.searchVm.searchState) { isLoading, searchValue ->
        updateEmptyText(isLoading, searchValue)
      }
    }

    scope.launchNow {
      listVm.focusRequests.collect {
        yield()
        CollaborationToolsUIUtil.focusPanel(mainPanel)
      }
    }
  }

  private fun updateEmptyText(isLoading: Boolean, searchValue: GHPRListSearchValue) {
    emptyText.clear()
    if (isLoading) {
      emptyText.appendText(CollaborationToolsBundle.message("review.list.empty.state.loading"))
      return
    }

    if (searchValue.filterCount == 0) {
      emptyText.appendText(GithubBundle.message("pull.request.list.nothing.loaded", listVm.repository))
    }
    else {
      emptyText
        .appendText(GithubBundle.message("pull.request.list.no.matches"))
        .appendSecondaryText(GithubBundle.message("pull.request.list.filters.clear"), SimpleTextAttributes.LINK_ATTRIBUTES) {
          listVm.searchVm.searchState.value = GHPRListSearchValue.EMPTY
        }
    }
  }

  private fun createErrorPanel(project: Project, scope: CoroutineScope, account: GithubAccount): JComponent {
    val errorPresenter = createErrorStatusPresenter(project, account)
    val errorPanel = ErrorStatusPanelFactory.create(scope, listVm.error, errorPresenter)
    return JPanel(SingleComponentCenteringLayout()).apply {
      add(errorPanel)
    }
  }

  private fun createErrorStatusPresenter(project: Project, account: GithubAccount): ErrorStatusPresenter.Text<Throwable> {
    val errorHandler = GHApiLoadingErrorHandler(project, account) {
      listVm.reload()
    }

    return ErrorStatusPresenter.simple(
      GithubBundle.message("pull.request.list.cannot.load"),
      descriptionProvider = { error ->
        if (error is GithubAuthenticationException) GithubBundle.message("pull.request.list.error.authorization")
        else GHHtmlErrorPanel.getLoadingErrorText(error)
      },
      actionProvider = errorHandler::getActionForError
    )
  }
}