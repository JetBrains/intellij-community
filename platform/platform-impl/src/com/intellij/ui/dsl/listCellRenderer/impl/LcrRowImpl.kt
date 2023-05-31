// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.*
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

private const val HORIZONTAL_GAP = 4

@ApiStatus.Internal
internal class LcrRowImpl<T> : LcrRow<T>, ListCellRenderer<T> {

  private lateinit var _renderer: (list: JList<out T>, value: T, index: Int, isSelected: Boolean, cellHasFocus: Boolean, rowParams: RowParams) -> Unit
  private val selectablePanel = SelectablePanel()

  /**
   * Content panel allows to trim components that could go outside of selection. It's better to implement that on layout level later
   */
  private val content = JPanel(GridLayout())
  private val builder = RowsGridBuilder(content)
  private val cells = mutableListOf<LcrCellBaseImpl>()

  init {
    content.isOpaque = false

    selectablePanel.apply {
      layout = BorderLayout()
      add(content, BorderLayout.CENTER)
    }
    builder.resizableRow()
  }

  override fun icon(init: (LcrInitParams.() -> Unit)?): LcrIcon {
    val initParams = LcrInitParamsImpl()
    if (init != null) {
      initParams.init()
    }
    val result = LcrIconImpl()
    add(result, initParams, false)
    return result
  }

  override fun text(init: (LcrTextInitParams.() -> Unit)?): LcrText {
    val initParams = LcrTextInitParamsImpl()
    if (init != null) {
      initParams.init()
    }
    val result = LcrTextImpl(initParams)
    add(result, initParams, true)
    return result
  }

  override fun cell(component: JComponent, init: (LcrCellInitParams.() -> Unit)?): LcrCell {
    val initParams = LcrCellInitParamsImpl()
    if (init != null) {
      initParams.init()
    }

    if (initParams.stripHorizontalInsets) {
      stripHorizontalInsets(component)
    }
    else {
      component.putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
    }

    component.isOpaque = initParams.opaque

    val result = LcrCellImpl(component)
    add(result, initParams, isBaselineComponent(component))
    return result
  }

  override fun renderer(init: (list: JList<out T>, value: T, index: Int, isSelected: Boolean, cellHasFocus: Boolean, rowParams: RowParams) -> Unit) {
    if (this::_renderer.isInitialized) {
      throw UiDslException("Only one renderer is allowed")
    }
    _renderer = init
  }

  override fun renderer(init: (value: T) -> Unit) {
    renderer { _, value, _, _, _, _ -> init(value) }
  }

  fun onInitFinished() {
    if (!this::_renderer.isInitialized) {
      throw UiDslException("Renderer must be specified, see LcrRow.renderer")
    }
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val bg = if (isSelected) JBUI.CurrentTheme.List.Selection.background(cellHasFocus) else list.background
    if (ExperimentalUI.isNewUI()) {
      // Update height/insets every time, so IDE scaling is applied
      selectablePanel.apply {
        selectionArc = 8
        selectionInsets = JBInsets.create(0, 12)
        border = JBUI.Borders.empty(0, 20)
        preferredHeight = JBUI.CurrentTheme.List.rowHeight()

        background = list.background
        selectionColor = bg
      }
    }
    else {
      selectablePanel.apply {
        background = bg
        border = JBUI.Borders.empty(UIUtil.getListCellVPadding(), UIUtil.getListCellHPadding())
      }
    }

    for (cell in cells) {
      cell.init(list, isSelected, cellHasFocus)
    }

    _renderer.invoke(list, value, index, isSelected, cellHasFocus, RowParamsImpl(selectablePanel))
    return selectablePanel
  }

  private fun add(cell: LcrCellBaseImpl, initParams: LcrInitParamsImpl, baselineAlign: Boolean) {
    cells.add(cell)
    builder.cell(cell.component, gaps = if (builder.x == 0) UnscaledGaps.EMPTY else UnscaledGaps(left = HORIZONTAL_GAP),
                 resizableColumn = initParams.grow, baselineAlign = baselineAlign)
  }
}

private fun isBaselineComponent(component: JComponent): Boolean {
  // can be extended later
  return component is JLabel ||
         component is SimpleColoredComponent
}