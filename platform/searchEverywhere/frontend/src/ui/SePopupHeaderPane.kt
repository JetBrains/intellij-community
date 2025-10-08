// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.PreviewAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereTabsShortcutsUtils
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.vm.SeTabVm
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.bindSelectedTabIn
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

@Internal
class SePopupHeaderPane(
  private val project: Project?,
  coroutineScope: CoroutineScope,
  private val configuration: StateFlow<Configuration>,
  private val resizeIfNecessary: () -> Unit,
) : NonOpaquePanel() {
  private lateinit var tabbedPane: JBTabbedPane
  private val tabShortcuts = SearchEverywhereTabsShortcutsUtils.createShortcutsMap()
  private val panel: DialogPanel
  private var toolbar: ActionToolbar = ActionManager.getInstance().createActionToolbar("search.everywhere.toolbar", DefaultActionGroup(), true)
  private var toolbarListenerDisposable: Disposable? = null

  private val tabFilterContainer: JPanel = object : JPanel() {
    override fun getPreferredSize(): Dimension {
      val dimension = components.firstOrNull()?.preferredSize ?: Dimension(0, 0)
      return Dimension(dimension.width.coerceAtMost(MAX_FILTER_WIDTH), 0)
    }
  }.apply {
    layout = GridLayout()
    background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
  }

  init {
    background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND

    panel = panel {
      row {
        tabbedPane = tabbedPaneHeader()
          .customize(UnscaledGaps.EMPTY)
          .applyToComponent {
            font = JBFont.regular()
            background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
            isFocusable = false
          }
          .component

        setFilterActions(emptyList(), null, false)
        cell(tabFilterContainer).align(AlignY.FILL + AlignX.RIGHT).resizableColumn()
      }

    }

    panel.background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND

    panel.border = JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                                         JBUI.CurrentTheme.BigPopup.headerBorder())

    configuration.value.tabs.ifEmpty {
      listOf(Tab(SeAllTab.NAME, SeAllTab.ID, SeAllTab.ID))
    }.forEach { tab ->
      tabbedPane.addTab(tab.name, null, JPanel(), tabShortcuts[tab.id])
    }

    add(panel)

    tabbedPane.addChangeListener {
      val tabs = configuration.value.tabs
      if (tabbedPane.selectedIndex < 0 || tabbedPane.selectedIndex >= tabs.size) return@addChangeListener

      val tabId = tabs[tabbedPane.selectedIndex].reportableId
      SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(project,
                                                             SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(tabId),
                                                             SearchEverywhereUsageTriggerCollector.IS_SPLIT.with(true))
    }

    var selectedTabBindingJob: Job? = null

    coroutineScope.launch {
      configuration.collectLatest { configuration ->
        withContext(Dispatchers.EDT) {
          tabbedPane.removeAll()
          selectedTabBindingJob?.cancel()

          if (configuration.tabs.isNotEmpty()) {
            for (tab in configuration.tabs) {
              tabbedPane.addTab(tab.name, null, JPanel(), tabShortcuts[tab.id])
            }

            selectedTabBindingJob = tabbedPane.bindSelectedTabIn(configuration.selectedTab, coroutineScope)
          }
          else {
            tabbedPane.addTab(SeAllTab.NAME, null, JPanel(), null)
          }
        }

        this@launch.launch {
          configuration.deferredTabs.collect { tab ->
            withContext(Dispatchers.EDT) {
              tabbedPane.addTab(tab.name, null, JPanel(), tabShortcuts[tab.id])
            }
          }
        }
      }
    }
  }

  override fun removeNotify() {
    toolbarListenerDisposable?.let { Disposer.dispose(it) }
    toolbarListenerDisposable = null
    super.removeNotify()
  }

  fun setFilterActions(actions: List<AnAction>, showInFindToolWindowAction: AnAction?, isPreviewEnabled: Boolean) {
    toolbarListenerDisposable?.let { Disposer.dispose(it) }
    val toolbarListenerDisposable = Disposer.newDisposable()
    this.toolbarListenerDisposable = toolbarListenerDisposable

    val actionGroup = DefaultActionGroup(actions)
    if (isPreviewEnabled) {
      actionGroup.add(PreviewAction())
    }
    showInFindToolWindowAction?.let { actionGroup.add(it) }
    toolbar = ActionManager.getInstance().createActionToolbar("search.everywhere.toolbar", actionGroup, true)
    toolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)
    toolbar.targetComponent = this
    val toolbarComponent = toolbar.getComponent()
    toolbarComponent.setOpaque(false)
    toolbarComponent.setBorder(JBUI.Borders.empty(2, 18, 2, 9))

    setFilterComponent(toolbarComponent)
    toolbar.addListener(object : ActionToolbarListener {
      override fun actionsUpdated() {
        ApplicationManager.getApplication().invokeLater {
          resizeIfNecessary()
        }
      }
    }, toolbarListenerDisposable)

    resizeIfNecessary()
  }

  fun updateActionsAsync() {
    toolbar.updateActionsAsync()
  }

  private fun setFilterComponent(filterComponent: JComponent?) {
    val oldCount = tabFilterContainer.componentCount

    tabFilterContainer.removeAll()
    if (filterComponent != null) {
      RowsGridBuilder(tabFilterContainer)
        .row(resizable = true).cell(filterComponent, verticalAlign = VerticalAlign.CENTER)
    }

    if (tabFilterContainer.componentCount + oldCount > 0) {
      tabFilterContainer.revalidate()
      tabFilterContainer.repaint()
    }
  }

  class Tab(val name: @Nls String, val id: String, val reportableId: String) {
    constructor(tabVm: SeTabVm) : this(tabVm.name, tabVm.tabId, tabVm.reportableTabId)
  }

  class Configuration(
    val tabs: List<Tab>,
    val deferredTabs: Flow<Tab>,
    val selectedTab: MutableStateFlow<Int>,
    val showInFindToolWindowAction: AnAction?
  ) {
    companion object {
      fun createInitial(initialTabs: List<Tab>,
                        selectedTabId: String): Configuration =
        Configuration(initialTabs, emptyFlow(), MutableStateFlow(initialTabs.indexOfFirst { it.id == selectedTabId }), null)
    }
  }

  companion object {
    private const val MAX_FILTER_WIDTH = 100
  }
}
