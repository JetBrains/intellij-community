// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereTabsShortcutsUtils
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionToolbarListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.vm.SeDummyTabVm
import com.intellij.platform.searchEverywhere.frontend.vm.SeTabVm
import com.intellij.platform.searchEverywhere.frontend.withPrevious
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.dsl.builder.tabbedPaneHeader
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

@OptIn(ExperimentalCoroutinesApi::class)
@Internal
class SePopupHeaderPane(
  private val project: Project?,
  coroutineScope: CoroutineScope,
  private val configurationFlow: StateFlow<Configuration>,
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

        setFilterActions(emptyList(), null)
        cell(tabFilterContainer).align(AlignY.FILL + AlignX.RIGHT).resizableColumn()
      }
    }

    panel.background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND

    panel.border = JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                                         JBUI.CurrentTheme.BigPopup.headerBorder())

    val initialConfiguration = configurationFlow.value

    initialConfiguration.tabs.ifEmpty {
      listOf(Tab(SeAllTab.NAME, SeAllTab.ID, SeAllTab.PRIORITY, SeAllTab.ID))
    }.forEach { tab ->
      tabbedPane.addTab(tab.name, null, TabComponent(tab.id), tabShortcuts[tab.id])
    }

    setSelectedIndexSafe(initialConfiguration.selectedIndexFlow.value)

    add(panel)

    tabbedPane.addChangeListener {
      val tabs = configurationFlow.value.tabs
      if (tabbedPane.selectedIndex < 0 || tabbedPane.selectedIndex >= tabs.size) return@addChangeListener

      val tabId = tabs[tabbedPane.selectedIndex].reportableId
      SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(project,
                                                             SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(tabId),
                                                             SearchEverywhereUsageTriggerCollector.IS_SPLIT.with(true))
    }

    coroutineScope.launch {
      configurationFlow.withPrevious().collectLatest { (old, new) ->
        updateTabs(old ?: initialConfiguration, new)
      }
    }
  }

  private suspend fun updateTabs(old: Configuration, new: Configuration) = coroutineScope {
    withContext(Dispatchers.EDT) {
      // Select a tab from the new configuration if the old configuration doesn't contain the newly selected tab id,
      // or take the selected tab directly from UI
      val tabIdToSelect = new.selectedTabOrNull?.id?.takeIf { newSelectedId ->
        !old.tabs.any { newSelectedId == it.id }
      } ?: (tabbedPane.selectedComponent as? TabComponent)?.tabId

      tabbedPane.removeAll()

      if (new.tabs.isEmpty()) {
        tabbedPane.addTab(SeAllTab.NAME, null, TabComponent(SeAllTab.ID), null)
        return@withContext
      }

      for (tab in new.tabs) {
        tabbedPane.addTab(tab.name, null, TabComponent(tab.id), tabShortcuts[tab.id])
      }

      new.selectedIndexFlow.value = new.tabs.indexOfTabWithIdOrZero(tabIdToSelect)
      setSelectedIndexSafe(new.selectedIndexFlow.value)
    }

    bindSelectedTab(new)
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private suspend fun bindSelectedTab(configuration: Configuration) = coroutineScope {
    val changeListener = javax.swing.event.ChangeListener {
      configuration.selectedIndexFlow.value = tabbedPane.selectedIndex
    }

    withContext(Dispatchers.UI) {
      tabbedPane.addChangeListener(changeListener)
    }

    launch {
      configuration.selectedIndexFlow.collectLatest { tabIndex ->
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (tabbedPane.selectedIndex != tabIndex && tabIndex >= 0 && tabIndex < tabbedPane.tabCount) {
            tabbedPane.removeChangeListener(changeListener)
            tabbedPane.selectedIndex = tabIndex
            tabbedPane.addChangeListener(changeListener)
          }
        }
      }
    }

    awaitCancellationAndInvoke(Dispatchers.UI) {
      tabbedPane.removeChangeListener(changeListener)
    }
  }

  override fun removeNotify() {
    toolbarListenerDisposable?.let { Disposer.dispose(it) }
    toolbarListenerDisposable = null
    super.removeNotify()
  }

  private fun setSelectedIndexSafe(index: Int) {
    index.takeIf {
      it >= 0 && it < tabbedPane.tabCount
    }?.let {
      tabbedPane.selectedIndex = it
    }
  }

  fun setFilterActions(actions: List<AnAction>, showInFindToolWindowAction: AnAction?) {
    toolbarListenerDisposable?.let { Disposer.dispose(it) }
    val toolbarListenerDisposable = Disposer.newDisposable()
    project?.let { Disposer.register(it, toolbarListenerDisposable) }
    this.toolbarListenerDisposable = toolbarListenerDisposable

    val actionGroup = DefaultActionGroup(actions)
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

  class Tab(val name: @Nls String, val id: String, val priority: Int, val reportableId: String) {
    constructor(tabVm: SeTabVm) : this(tabVm.name, tabVm.tabId, tabVm.priority, tabVm.reportableTabId)
  }

  class Configuration(
    val tabs: List<Tab>,
    val selectedIndexFlow: MutableStateFlow<Int>
  ) {
    val selectedTabOrNull: Tab? get() = tabs.getOrNull(selectedIndexFlow.value)

    companion object {
      fun createInitial(
        initialTabs: List<SeDummyTabVm>,
        selectedTabId: String,
      ): Configuration = initialTabs.let {
        val initialTabs = initialTabs.map { Tab(it) }
        Configuration(initialTabs, MutableStateFlow(initialTabs.indexOfTabWithIdOrZero(selectedTabId)))
      }
    }
  }

  private class TabComponent(val tabId: String) : JPanel()

  companion object {
    private const val MAX_FILTER_WIDTH = 100
  }
}

private fun List<SePopupHeaderPane.Tab>.indexOfTabWithIdOrZero(tabId: String?): Int = tabId?.let {
  indexOfFirst { it.id == tabId }.takeIf { it != -1 }
} ?: 0
