// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.list

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.scroll.BoundedRangeModelThresholdListener
import com.intellij.vcs.log.ui.frame.ProgressStripe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestShortDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabFiltersPanelFactory
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.event.ChangeEvent

internal class GitLabMergeRequestsPanelFactory {

  fun create(project: Project, scope: CoroutineScope, listVm: GitLabMergeRequestsListViewModel): JComponent {
    val listModel = collectMergeRequests(scope, listVm)
    val listMergeRequests = GitLabMergeRequestsListComponentFactory.create(listModel, listVm.avatarIconsProvider)

    val listLoaderPanel = createListLoaderPanel(scope, listVm, listMergeRequests)
    val progressStripe = ProgressStripe(
      listLoaderPanel,
      scope.nestedDisposable(),
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    ).apply {
      scope.launch {
        listVm.loadingState.collect {
          if (it) startLoadingImmediately() else stopLoading()
        }
      }
    }

    val searchPanel = createSearchPanel(scope, listVm)

    GitLabMergeRequestsListController(project, scope, listVm, listMergeRequests.emptyText, listLoaderPanel, progressStripe)

    return JBUI.Panels.simplePanel(progressStripe)
      .addToTop(searchPanel)
      .andTransparent()
  }

  private fun collectMergeRequests(scope: CoroutineScope,
                                   listVm: GitLabMergeRequestsListViewModel): CollectionListModel<GitLabMergeRequestShortDTO> {
    val listModel = CollectionListModel<GitLabMergeRequestShortDTO>()
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

  private fun createListLoaderPanel(scope: CoroutineScope, listVm: GitLabMergeRequestsListViewModel, list: JComponent): JComponent {
    return ScrollPaneFactory.createScrollPane(list, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

      val model = verticalScrollBar.model
      val listener = object : BoundedRangeModelThresholdListener(model, 0.7f) {
        override fun onThresholdReached() {
          if (listVm.canLoadMoreState.value) {
            listVm.requestMore()
          }
        }
      }
      model.addChangeListener(listener)

      scope.launch {
        listVm.listDataFlow.collect {
          when (it) {
            is GitLabMergeRequestsListViewModel.ListDataUpdate.NewBatch -> {
              if (isShowing) {
                listener.stateChanged(ChangeEvent(listVm))
              }
            }
            GitLabMergeRequestsListViewModel.ListDataUpdate.Clear -> {
              if (isShowing) {
                listVm.requestMore()
              }
            }
          }
        }
      }
    }
  }

  private fun createSearchPanel(scope: CoroutineScope, listVm: GitLabMergeRequestsListViewModel): JComponent {
    return GitLabFiltersPanelFactory(listVm.filterVm).create(scope)
  }
}