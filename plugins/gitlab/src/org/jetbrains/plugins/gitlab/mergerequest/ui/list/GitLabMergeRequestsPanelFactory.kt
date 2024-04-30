// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.list.ReviewListUtil.wrapWithLazyVerticalScroll
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CompositeShortcutSet
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestActionPlaces
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabFiltersPanelFactory
import javax.swing.JComponent

internal class GitLabMergeRequestsPanelFactory {

  fun create(scope: CoroutineScope,
             accountVm: GitLabAccountViewModel,
             listVm: GitLabMergeRequestsListViewModel): JComponent {

    val listModel = collectMergeRequests(scope, listVm)
    val list = GitLabMergeRequestsListComponentFactory.create(listModel, listVm.avatarIconsProvider)

    val listLoaderPanel = wrapWithLazyVerticalScroll(scope, list, listVm::requestMore)
    val listWrapper = Wrapper()
    val progressStripe = CollaborationToolsUIUtil.wrapWithProgressStripe(scope, listVm.loading, listWrapper).also { panel ->
      DataManager.registerDataProvider(panel) { dataId ->
        when {
          GitLabMergeRequestsActionKeys.SELECTED.`is`(dataId) -> list.takeIf { it.isShowing }?.selectedValue
          GitLabMergeRequestsActionKeys.REVIEW_LIST_VM.`is`(dataId) -> listVm
          else -> null
        }
      }
      val shortcuts = CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.DOUBLE_CLICK_1)
      ActionUtil.wrap("GitLab.Merge.Request.Show").registerCustomShortcutSet(shortcuts, panel)
    }
    ScrollableContentBorder.setup(listLoaderPanel, Side.TOP, progressStripe)

    val popupActionGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.List.Actions") as ActionGroup
    val place = GitLabMergeRequestActionPlaces.LIST_POPUP
    PopupHandler.installPopupMenu(progressStripe, popupActionGroup, place)
    PopupHandler.installPopupMenu(list, popupActionGroup, place)

    val searchPanel = createSearchPanel(scope, listVm)

    GitLabMergeRequestsListController(scope, accountVm, listVm, list.emptyText, listLoaderPanel, listWrapper)

    return JBUI.Panels.simplePanel(progressStripe)
      .addToTop(searchPanel)
      .andTransparent()
  }

  private fun collectMergeRequests(scope: CoroutineScope,
                                   listVm: GitLabMergeRequestsListViewModel): CollectionListModel<GitLabMergeRequestDetails> {
    val listModel = CollectionListModel<GitLabMergeRequestDetails>()
    scope.launch {
      listVm.listDataFlow.collect {
        listModel.replaceAll(it)
      }
    }

    return listModel
  }

  private fun createSearchPanel(scope: CoroutineScope, listVm: GitLabMergeRequestsListViewModel): JComponent {
    return GitLabFiltersPanelFactory(listVm.filterVm).create(scope)
  }
}