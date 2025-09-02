// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.list

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.wrapWithProgressStripe
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.list.ReviewListUtil.wrapWithLazyVerticalScroll
import com.intellij.collaboration.ui.util.toListModelIn
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchPanelFactory
import java.awt.FlowLayout
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListModel
import javax.swing.event.ListDataListener

@ApiStatus.Internal
object GHPRListPanelFactory {
  fun create(project: Project, cs: CoroutineScope, dataContext: GHPRDataContext, listVm: GHPRListViewModel): JComponent {
    val ghostUser = dataContext.securityService.ghostUser
    val currentUser = dataContext.securityService.currentUser

    val listModel = cs.childScope("EDT", Dispatchers.EDT).let { cs ->
      cs.scopedDelegatingListModel(listVm.loadedData.toListModelIn(cs))
    }
    val list = GHPRListComponentFactory(dataContext.interactionState, listModel)
      .create(listVm.avatarIconsProvider, ghostUser, currentUser)

    GHPRStatisticsCollector.logListOpened(project)

    val actionManager = ActionManager.getInstance()
    val searchPanel = GHPRSearchPanelFactory(listVm.searchVm, listVm.avatarIconsProvider).create(cs)

    val outdatedStatePanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUIScale.scale(5), 0)).apply {
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(4, 0)
      add(JLabel(GithubBundle.message("pull.request.list.outdated")))
      add(ActionLink(GithubBundle.message("pull.request.list.refresh")) {
        listVm.reload()
      })

      isVisible = false
    }

    cs.launchNow {
      combineAndCollect(listVm.isLoading, listVm.error, listVm.outdated) { loading, error, outdated ->
        outdatedStatePanel.isVisible = outdated && (!loading && error == null)
      }
    }

    val controlsPanel = VerticalListPanel().apply {
      add(searchPanel)
      add(outdatedStatePanel)
    }

    val listLoaderPanel = wrapWithLazyVerticalScroll(cs, list, listVm::requestMore)
    val listWrapper = Wrapper()
    val progressStripe = wrapWithProgressStripe(cs, listVm.isLoading, listWrapper)
    ScrollableContentBorder.setup(listLoaderPanel, Side.TOP, progressStripe)

    GHPRListPanelController(project, cs, listVm, list.emptyText, listLoaderPanel, listWrapper)

    return JBUI.Panels.simplePanel(progressStripe).addToTop(controlsPanel).andTransparent().also {
      val listController = GHPRListControllerImpl(listVm)
      DataManager.registerDataProvider(it) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER.`is`(dataId) -> listController
          GHPRActionKeys.PULL_REQUEST_ID.`is`(dataId) -> list.selectedValue?.prId
          GHPRActionKeys.PULL_REQUEST_URL.`is`(dataId) -> list.selectedValue?.url
          else -> null
        }
      }
      actionManager.getAction("Github.PullRequest.List.Reload").registerCustomShortcutSet(it, cs.nestedDisposable())
    }
  }

  private fun <T> CoroutineScope.scopedDelegatingListModel(delegate: ListModel<T>) =
    object : ListModel<T> by delegate {
      private val listeners = CopyOnWriteArrayList<ListDataListener>()

      init {
        launchNow {
          try {
            awaitCancellation()
          }
          finally {
            listeners.forEach {
              delegate.removeListDataListener(it)
            }
          }
        }
      }

      override fun addListDataListener(l: ListDataListener) {
        if (!isActive) return
        listeners.add(l)
        delegate.addListDataListener(l)
      }

      override fun removeListDataListener(l: ListDataListener) {
        delegate.removeListDataListener(l)
        listeners.remove(l)
      }
    }
}