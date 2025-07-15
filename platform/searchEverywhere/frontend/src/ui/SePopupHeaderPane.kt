// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereTabsShortcutsUtils
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbarListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

@Internal
class SePopupHeaderPane(
  private val project: Project?,
  tabs: @Nls List<Tab>,
  selectedTabState: MutableStateFlow<Int>,
  coroutineScope: CoroutineScope,
  private val showInFindToolWindowAction: AnAction,
  private val resizeIfNecessary: () -> Unit
): NonOpaquePanel() {
  private lateinit var tabbedPane: JBTabbedPane
  private val tabInfos = mutableListOf<Tab>().apply { addAll(tabs) }
  private val tabShortcuts = SearchEverywhereTabsShortcutsUtils.createShortcutsMap()
  private val panel: DialogPanel
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

        setFilterActions(emptyList())
        cell(tabFilterContainer).align(AlignY.FILL + AlignX.RIGHT).resizableColumn()
      }

    }

    panel.background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND

    val headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets()
    @Suppress("UseDPIAwareBorders")
    panel.border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      EmptyBorder(0, headerInsets.left, 0, headerInsets.right))

    for (tab in tabInfos) {
      tabbedPane.addTab(tab.name, null, JPanel(), tabShortcuts[tab.id])
    }

    add(panel)

    tabbedPane.bindSelectedTabIn(selectedTabState, coroutineScope)

    tabbedPane.addChangeListener {
      val tabId = tabInfos[tabbedPane.selectedIndex].reportableId
      SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(project,
                                                             SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(tabId),
                                                             SearchEverywhereUsageTriggerCollector.IS_SPLIT.with(true))
    }
  }

  fun setFilterActions(actions: List<AnAction>) {
    toolbarListenerDisposable?.let { Disposer.dispose(it) }
    val toolbarListenerDisposable = Disposer.newDisposable()
    this.toolbarListenerDisposable = toolbarListenerDisposable

    val actionGroup = DefaultActionGroup(actions)
    actionGroup.add(showInFindToolWindowAction)
    val toolbar = ActionManager.getInstance().createActionToolbar("search.everywhere.toolbar", actionGroup, true)
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

  fun addTab(tab: Tab) {
    tabInfos.add(tab)
    tabbedPane.addTab(tab.name, null, JPanel(), tabShortcuts[tab.id])
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

  companion object {
    private const val MAX_FILTER_WIDTH = 100
  }
}
