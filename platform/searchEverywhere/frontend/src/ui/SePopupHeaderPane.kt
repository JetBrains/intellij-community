// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.searchEverywhere.frontend.SeFilterActionsPresentation
import com.intellij.platform.searchEverywhere.frontend.SeFilterComponentPresentation
import com.intellij.platform.searchEverywhere.frontend.SeFilterPresentation
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
class SePopupHeaderPane(tabNames: @Nls List<String>,
                        selectedTabState: MutableStateFlow<Int>,
                        coroutineScope: CoroutineScope,
                        toolbar: JComponent? = null): NonOpaquePanel() {
  private lateinit var tabbedPane: JBTabbedPane
  private val panel: DialogPanel
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

        cell(tabFilterContainer).align(AlignY.FILL + AlignX.RIGHT).resizableColumn()

        if (toolbar != null) {
          toolbar.putClientProperty(ActionToolbarImpl.USE_BASELINE_KEY, true)
          cell(toolbar)
            .align(AlignX.RIGHT)
            .customize(UnscaledGaps(left = 18))
        }
      }

    }

    panel.background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND

    val headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets()
    @Suppress("UseDPIAwareBorders")
    panel.border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      EmptyBorder(0, headerInsets.left, 0, headerInsets.right))

    for (tab in tabNames) {
      //@NlsSafe val shortcut = shortcutSupplier.apply(tab.id)
      tabbedPane.addTab(tab, null, JPanel(), tab)
    }

    add(panel)

    tabbedPane.bindSelectedTabIn(selectedTabState, coroutineScope)
  }

  fun setFilterPresentation(filterPresentation: SeFilterPresentation?) {
    when (filterPresentation) {
      is SeFilterActionsPresentation -> setFilterActions(filterPresentation.getActions())
      is SeFilterComponentPresentation -> setFilterComponent(filterPresentation.getComponent())
      null -> setFilterComponent(null)
    }
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

  private fun setFilterActions(actions: List<AnAction>) {
    val actionGroup = DefaultActionGroup(actions)
    val toolbar = ActionManager.getInstance().createActionToolbar("search.everywhere.toolbar", actionGroup, true)
    toolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)
    toolbar.targetComponent = this
    val toolbarComponent = toolbar.getComponent()
    toolbarComponent.setOpaque(false)
    toolbarComponent.setBorder(JBUI.Borders.empty(2, 18, 2, 9))

    setFilterComponent(toolbarComponent)
  }

  companion object {
    private const val MAX_FILTER_WIDTH = 100
  }
}
