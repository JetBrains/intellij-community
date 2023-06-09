// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeLater
import com.intellij.ui.*
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabFiltersPanelFactory
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

internal class GitLabMergeRequestsPanelFactory {

  fun create(scope: CoroutineScope,
             accountVm: GitLabAccountViewModel,
             listVm: GitLabMergeRequestsListViewModel): JComponent {

    val listModel = collectMergeRequests(scope, listVm)
    val list = GitLabMergeRequestsListComponentFactory.create(listModel, listVm.avatarIconsProvider)

    val listLoaderPanel = createListLoaderPanel(listVm, list)
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
      EmptyAction.registerWithShortcutSet("GitLab.Merge.Request.Show", shortcuts, panel)
    }
    ScrollableContentBorder.setup(listLoaderPanel, Side.TOP, progressStripe)

    val popupActionGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.List.Actions") as ActionGroup
    PopupHandler.installPopupMenu(progressStripe, popupActionGroup, ActionPlaces.POPUP)
    PopupHandler.installPopupMenu(list, popupActionGroup, ActionPlaces.POPUP)

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
      var firstEvent = true
      listVm.listDataFlow.collect {
        when (it) {
          is GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch -> {
            if (firstEvent) listModel.add(it.newList)
            else listModel.add(it.batch)
          }
          GitLabMergeRequestsListViewModel.ListDataUpdate.Clear -> listModel.removeAll()
        }
        firstEvent = false
      }
    }

    return listModel
  }

  private fun createListLoaderPanel(listVm: GitLabMergeRequestsListViewModel, list: JList<*>): JScrollPane {
    return ScrollPaneFactory.createScrollPane(list, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

      val model = verticalScrollBar.model
      val listener = object : BoundedRangeModelThresholdListener(model, 0.7f) {
        override fun onThresholdReached() {
          listVm.requestMore()
        }
      }
      model.addChangeListener(listener)

      list.model.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent) {
          invokeLater {
            if (list.isShowing) {
              listener.stateChanged(ChangeEvent(list))
            }
          }
        }

        override fun intervalRemoved(e: ListDataEvent) = Unit
        override fun contentsChanged(e: ListDataEvent) = Unit
      })
    }
  }

  private fun createSearchPanel(scope: CoroutineScope, listVm: GitLabMergeRequestsListViewModel): JComponent {
    return GitLabFiltersPanelFactory(listVm.filterVm).create(scope)
  }
}