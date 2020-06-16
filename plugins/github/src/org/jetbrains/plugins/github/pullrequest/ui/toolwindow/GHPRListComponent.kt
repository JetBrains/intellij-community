// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.ide.DataManager
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.*
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRListUpdatesChecker
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery
import org.jetbrains.plugins.github.pullrequest.search.GHPRSearchQueryHolder
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.ui.GHHandledErrorPanelModel
import org.jetbrains.plugins.github.ui.GHHtmlErrorPanel
import org.jetbrains.plugins.github.ui.util.BoundedRangeModelThresholdListener
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

internal object GHPRListComponent {

  fun create(project: Project,
             dataContext: GHPRDataContext,
             disposable: Disposable): JComponent {

    val actionManager = ActionManager.getInstance()

    val listLoader = dataContext.listLoader
    val listModel = CollectionListModel(listLoader.loadedData)
    listLoader.addDataListener(disposable, object : GHListLoader.ListDataListener {
      override fun onDataAdded(startIdx: Int) {
        val loadedData = listLoader.loadedData
        listModel.add(loadedData.subList(startIdx, loadedData.size))
      }

      override fun onDataUpdated(idx: Int) = listModel.setElementAt(listLoader.loadedData[idx], idx)
      override fun onDataRemoved(data: Any) {
        (data as? GHPullRequestShort)?.let { listModel.remove(it) }
      }

      override fun onAllDataRemoved() = listModel.removeAll()
    })

    val list = object : JBList<GHPullRequestShort>(listModel) {

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

    val avatarIconsProvider = dataContext.avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, list)
    val renderer = GHPRListCellRenderer(avatarIconsProvider, openButtonViewModel)
    list.cellRenderer = renderer
    UIUtil.putClientProperty(list, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

    val searchQueryHolder = dataContext.searchHolder
    val searchStringModel = SingleValueModel(searchQueryHolder.queryString)
    searchQueryHolder.addQueryChangeListener(disposable) {
      if (searchStringModel.value != searchQueryHolder.queryString)
        searchStringModel.value = searchQueryHolder.queryString
    }
    searchStringModel.addValueChangedListener {
      searchQueryHolder.queryString = searchStringModel.value
    }

    ListEmptyTextController(listLoader, searchQueryHolder, list.emptyText, disposable)

    val search = GHPRSearchPanel.create(project, searchStringModel).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    }

    val outdatedStatePanel = JPanel(FlowLayout(FlowLayout.LEFT, UI.scale(5), 0)).apply {
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty(4, 0)
      add(JLabel(GithubBundle.message("pull.request.list.outdated")))
      add(LinkLabel<Any?>(GithubBundle.message("pull.request.list.refresh"), null) { _, _ ->
        listLoader.reset()
      })

      isVisible = false
    }
    OutdatedPanelController(listLoader, dataContext.listUpdatesChecker, outdatedStatePanel, disposable)

    val errorHandler = GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
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
      add(search, VerticalLayout.FILL_HORIZONTAL)
      add(outdatedStatePanel, VerticalLayout.FILL_HORIZONTAL)
      add(errorPane, VerticalLayout.FILL_HORIZONTAL)
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

  private fun createListLoaderPanel(loader: GHListLoader<*>, list: JBList<GHPullRequestShort>, disposable: Disposable): JComponent {

    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      isOpaque = false
      viewport.isOpaque = false

      verticalScrollBar.apply {
        isOpaque = true
        UIUtil.putClientProperty(this, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, false)
      }

      BoundedRangeModelThresholdListener.install(verticalScrollBar) {
        if (loader.canLoadMore()) loader.loadMore()
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

      if (searchHolder.query != GHPRSearchQuery.DEFAULT) {
        emptyText.appendText(GithubBundle.message("pull.request.list.no.matches"))
          .appendSecondaryText(GithubBundle.message("pull.request.list.reset.filters"), SimpleTextAttributes.LINK_ATTRIBUTES,
                               ActionListener {
                                 searchHolder.query = GHPRSearchQuery.DEFAULT
                               })
      }
      else {
        emptyText.appendText(GithubBundle.message("pull.request.list.nothing.loaded"))
          .appendSecondaryText(GithubBundle.message("pull.request.list.refresh"), SimpleTextAttributes.LINK_ATTRIBUTES, ActionListener {
            listLoader.reset()
          })
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