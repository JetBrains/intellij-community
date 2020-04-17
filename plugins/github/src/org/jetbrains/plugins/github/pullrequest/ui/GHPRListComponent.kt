// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.ide.DataManager
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.search.GithubPullRequestSearchPanel
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.ListSelectionModel

internal object GHPRListComponent {

  fun create(project: Project,
             dataContext: GHPRDataContext,
             avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
             disposable: Disposable): JComponent {

    val actionManager = ActionManager.getInstance()

    val list = object : JBList<GHPullRequestShort>(dataContext.listModel) {

      override fun getToolTipText(event: MouseEvent): String? {
        val childComponent = ListUtil.getDeepestRendererChildComponentAt(this, event.point)
        if (childComponent !is JComponent) return null
        return childComponent.toolTipText
      }
    }.apply {
      setExpandableItemsEnabled(false)
      emptyText.clear()
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    }.also {
      ScrollingUtil.installActions(it)
      ListUtil.installAutoSelectOnMouseMove(it)
      GithubUIUtil.Lists.installSelectionOnFocus(it)
      GithubUIUtil.Lists.installSelectionOnRightClick(it)
      DataManager.registerDataProvider(it) { dataId ->
        if (GHPRActionKeys.SELECTED_PULL_REQUEST.`is`(dataId)) it.selectedValue else null
      }
      PopupHandler.installSelectionListPopup(it,
                                             actionManager.getAction("Github.PullRequest.ToolWindow.List.Popup") as ActionGroup,
                                             ActionPlaces.UNKNOWN, actionManager)
      val shortcuts = CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.DOUBLE_CLICK_1)
      EmptyAction.registerWithShortcutSet("Github.PullRequest.Show", shortcuts, it)
      ListSpeedSearch(it) { item -> item.title }
    }

    val openButtonViewModel = GHPROpenButtonViewModel()
    installOpenButtonListeners(list, openButtonViewModel)

    val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
    val renderer = GHPRListCellRenderer(avatarIconsProvider, openButtonViewModel)
    list.cellRenderer = renderer
    UIUtil.putClientProperty(list, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

    val search = GithubPullRequestSearchPanel(project, AutoPopupController.getInstance(project), dataContext.searchHolder).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val listReloadAction = actionManager.getAction("Github.PullRequest.List.Reload") as RefreshAction
    val loaderPanel = GHPRListLoaderPanel(dataContext.listLoader, listReloadAction, list, search).apply {
      errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.account) {
        dataContext.listLoader.reset()
      }
      scrollPane.verticalScrollBar.apply {
        isOpaque = true
        UIUtil.putClientProperty(this, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, false)
      }
    }.also {
      listReloadAction.registerCustomShortcutSet(it, disposable)

      DataManager.registerDataProvider(it) { dataId ->
        if (GHPRActionKeys.SELECTED_PULL_REQUEST.`is`(dataId)) {
          if (list.isSelectionEmpty) null else list.selectedValue
        }
        else null
      }
    }

    Disposer.register(disposable, Disposable {
      Disposer.dispose(search)
      Disposer.dispose(loaderPanel)
    })

    return loaderPanel
  }

  private fun installOpenButtonListeners(list: JBList<GHPullRequestShort>,
                                         openButtonViewModel: GHPROpenButtonViewModel) {

    list.addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val point = e.point
        var index = list.locationToIndex(point)
        val cellBounds = list.getCellBounds(index, index)
        if (cellBounds == null || !cellBounds.contains(point)) index = -1

        openButtonViewModel.hoveredRowIndex = index
        openButtonViewModel.isButtonHovered = if (index == -1) false else isInsideButton(cellBounds, point)
        list.repaint()
      }
    })

    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        val point = event.point
        val index = list.locationToIndex(point)
        val cellBounds = list.getCellBounds(index, index)
        if (cellBounds == null || !cellBounds.contains(point)) return false

        if (isInsideButton(cellBounds, point)) {
          val action = ActionManager.getInstance().getAction("Github.PullRequest.Show")
          ActionUtil.invokeAction(action, list, ActionPlaces.UNKNOWN, event, null)
          return true
        }
        return false
      }
    }.installOn(list)
  }

  private fun isInsideButton(cellBounds: Rectangle, point: Point): Boolean {
    val iconSize = EmptyIcon.ICON_16.iconWidth
    val rendererRelativeX = point.x - cellBounds.x
    return (cellBounds.width - rendererRelativeX) <= iconSize
  }
}