// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.KotlinUIDslRenderer
import com.intellij.ui.dsl.listCellRenderer.LcrIconInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.render.RenderingUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.util.function.Supplier
import javax.swing.*
import javax.swing.plaf.basic.BasicComboPopup
import kotlin.math.max

private const val HORIZONTAL_GAP = 4

@ApiStatus.Internal
internal class LcrRowImpl<T>(private val renderer: LcrRow<T>.() -> Unit) : LcrRow<T>, ListCellRenderer<T> {

  private var listCellRendererParams: ListCellRendererParams<T>? = null

  private val selectablePanel = object : SelectablePanel(), KotlinUIDslRenderer {

    override fun getBaseline(width: Int, height: Int): Int {
      val baselineComponents = cells.filter { it.baselineAlign }.map { it.lcrCell.component }

      // JLabel doesn't have baseline if empty. Workaround similar like in BasicComboBoxUI.getBaseline method
      for (component in baselineComponents) {
        if (component is JLabel && component.text.isNullOrEmpty()) {
          component.text = " "
        }
      }
      setSize(width, height)
      doLayout()
      content.doLayout()
      var result = -1
      for (component in baselineComponents) {
        val componentBaseline = component.getBaseline(component.width, component.height)
        if (componentBaseline >= 0) {
          result = max(result, content.y + component.y + componentBaseline)
        }
      }
      return result
    }

    // Support disabled combobox color. Can be reworked later
    override fun setForeground(fg: Color?) {
      super.setForeground(fg)

      @Suppress("SENSELESS_COMPARISON")
      if (cells == null) {
        // Called while initialization
        return
      }

      for (cell in cells) {
        cell.lcrCell.component.foreground = fg
      }
    }
  }

  private val lcrCellCache = LcrCellCache()

  /**
   * Content panel allows to trim components that could go outside of selection. It's better to implement that on layout level later
   */
  private val content = JPanel(GridLayout())
  private val cells = mutableListOf<CellInfo>()

  init {
    content.isOpaque = false

    selectablePanel.apply {
      layout = BorderLayout()
      add(content, BorderLayout.CENTER)
    }
  }

  override val list: JList<out T>
    get() = listCellRendererParams!!.list
  override val value: T
    get() = listCellRendererParams!!.value
  override val index: Int
    get() = listCellRendererParams!!.index
  override val selected: Boolean
    get() = listCellRendererParams!!.selected
  override val hasFocus: Boolean
    get() = listCellRendererParams!!.hasFocus

  override var background: Color?
    get() = if (ExperimentalUI.isNewUI()) selectablePanel.selectionColor else selectablePanel.background
    set(value) {
      if (ExperimentalUI.isNewUI()) {
        selectablePanel.selectionColor = value
      }
      else {
        selectablePanel.background = value
      }
    }

  override fun icon(icon: Icon, init: (LcrIconInitParams.() -> Unit)?) {
    val initParams = LcrIconInitParamsImpl()
    if (init != null) {
      initParams.init()
    }

    val result = lcrCellCache.occupyIcon()
    result.init(icon)
    cells.add(CellInfo(result, initParams.grow, false))
  }

  override fun text(text: @Nls String, init: (LcrTextInitParams.() -> Unit)?) {
    val initParams = LcrTextInitParamsImpl()
    if (init != null) {
      initParams.init()
    }

    val result = lcrCellCache.occupyText()
    result.init(text, initParams, list, selected)
    cells.add(CellInfo(result, initParams.grow, true))
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    cells.clear()
    content.removeAll()
    lcrCellCache.release()

    val bg = if (isSelected) RenderingUtil.getSelectionBackground(list) else list.background
    val isComboBox = isComboBox(list)
    if (ExperimentalUI.isNewUI()) {
      // Update height/insets every time, so IDE scaling is applied
      selectablePanel.apply {
        if (isComboBox && index == -1) {
          // Renderer for selected item in collapsed ComboBox component
          selectablePanel.isOpaque = false
          selectionArc = 0
          selectionInsets = JBInsets.emptyInsets()
          border = null
          preferredHeight = null

          background = null
          selectionColor = null
        }
        else {
          selectablePanel.isOpaque = true
          selectionArc = 8
          if (isComboBox) {
            // todo borders for comboBox mode should be updated with implementation of IDEA-316042 Fix lists that open from dropdowns and combo boxes
            selectionInsets = JBInsets.create(0, 0)
            border = JBUI.Borders.empty(0, 8)
          }
          else {
            selectionInsets = JBInsets.create(0, 12)
            border = JBUI.Borders.empty(0, 20)
          }
          preferredHeight = JBUI.CurrentTheme.List.rowHeight()

          background = list.background
          selectionColor = bg
        }
      }
    }
    else {
      selectablePanel.apply {
        background = bg
        border = JBUI.Borders.empty(UIUtil.getListCellVPadding(), UIUtil.getListCellHPadding())
      }
    }

    listCellRendererParams = ListCellRendererParams(list, value, index, isSelected, cellHasFocus)
    renderer()

    val builder = RowsGridBuilder(content)
    builder.resizableRow()

    for (cell in cells) {
      // Row height is usually even. If components height is odd the component cannot be placed right in center.
      // Because of rounding it's placed a little bit higher which looks not good, especially for text. This patch fixes that
      val roundingTopGapPatch = cell.lcrCell.component.preferredSize.height % 2

      builder.cell(cell.lcrCell.component, gaps = UnscaledGaps(top = roundingTopGapPatch, left = if (builder.x == 0) 0 else HORIZONTAL_GAP),
                   horizontalAlign = if (cell.resizableColumn) HorizontalAlign.FILL else HorizontalAlign.LEFT,
                   verticalAlign = VerticalAlign.CENTER,
                   resizableColumn = cell.resizableColumn, baselineAlign = cell.baselineAlign)
    }

    selectablePanel.accessibleContext.accessibleName = getAccessibleName()

    return selectablePanel
  }

  private fun isComboBox(list: JList<*>): Boolean {
    return UIUtil.getParentOfType(BasicComboPopup::class.java, list) != null
  }

  @Nls
  private fun getAccessibleName(): String {
    val names = cells
      .map { it.lcrCell.component.accessibleContext.accessibleName.trim() }
      .filter { it.isNotEmpty() }

    // Comma gives a good pause between unrelated text for readers on Windows/MacOS
    return names.joinToString(", ")
  }
}

private data class ListCellRendererParams<T>(val list: JList<out T>,
                                             val value: T,
                                             val index: Int,
                                             val selected: Boolean,
                                             val hasFocus: Boolean)

private data class CellInfo(val lcrCell: LcrCellBaseImpl, val resizableColumn: Boolean, val baselineAlign: Boolean)

private class LcrCellCache {

  private val availableLcrCells = mutableListOf<LcrCellBaseImpl>()
  private val occupiedLcrCells = mutableListOf<LcrCellBaseImpl>()

  fun occupyIcon(): LcrIconImpl {
    return occupy { LcrIconImpl() }
  }

  fun occupyText(): LcrTextImpl {
    return occupy { LcrTextImpl() }
  }

  fun release() {
    availableLcrCells.addAll(occupiedLcrCells)
    occupiedLcrCells.clear()
  }

  private inline fun <reified T : LcrCellBaseImpl> occupy(factory: Supplier<T>): T {
    var result: T? = null
    for ((index, value) in availableLcrCells.withIndex()) {
      if (value is T) {
        result = value
        availableLcrCells.removeAt(index)
        break
      }
    }

    if (result == null) {
      result = factory.get()
    }

    occupiedLcrCells.add(result)
    return result
  }
}
