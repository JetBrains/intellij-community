// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestErrorUtil
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JPanel

internal class GitLabMergeRequestsListController(
  scope: CoroutineScope,
  accountVm: GitLabAccountViewModel,
  private val listVm: GitLabMergeRequestsListViewModel,
  private val emptyText: StatusText,
  private val listPanel: JComponent,
  private val mainPanel: Wrapper,
) {
  private val errorPanel: JComponent = createErrorPanel(scope, accountVm)

  init {
    scope.launch {
      listVm.error.collect { error ->
        mainPanel.setContent(if (error != null) errorPanel else listPanel)
        mainPanel.repaint()
      }
    }

    scope.launch {
      combineAndCollect(listVm.loading, listVm.filterVm.searchState) { isLoading, searchState ->
        updateEmptyText(isLoading, searchState, listVm.repository)
      }
    }
  }

  private fun updateEmptyText(isLoading: Boolean, searchState: GitLabMergeRequestsFiltersValue, repository: String) {
    emptyText.clear()

    if (isLoading) {
      emptyText.appendText(CollaborationToolsBundle.message("review.list.empty.state.loading"))
      return
    }

    if (searchState.filterCount == 0) {
      emptyText
        .appendText(GitLabBundle.message("merge.request.list.empty.state.matching.nothing", repository))
    }
    else {
      emptyText
        .appendText(GitLabBundle.message("merge.request.list.empty.state.matching.nothing.with.filters"))
        .appendSecondaryText(GitLabBundle.message("merge.request.list.empty.state.clear.filters"), SimpleTextAttributes.LINK_ATTRIBUTES) {
          listVm.filterVm.searchState.value = GitLabMergeRequestsFiltersValue.EMPTY
        }
    }
  }

  private fun createErrorPanel(scope: CoroutineScope, accountVm: GitLabAccountViewModel): JComponent {
    val errorPresenter = GitLabMergeRequestErrorUtil.createErrorStatusPresenter(
      accountVm,
      swingAction(GitLabBundle.message("merge.request.list.reload")) {
        listVm.reload()
      })
    val errorPanel = ErrorStatusPanelFactory.create(scope, listVm.error, errorPresenter)

    return JPanel(SingleComponentCenteringLayout()).apply {
      add(errorPanel)
    }
  }
}