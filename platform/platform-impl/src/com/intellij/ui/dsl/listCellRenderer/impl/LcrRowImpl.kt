// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.*
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
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.plaf.basic.BasicComboPopup
import kotlin.math.max

@ApiStatus.Internal
open class LcrRowImpl<T>(private val renderer: LcrRow<T>.() -> Unit) : LcrRow<T>, ListCellRenderer<T>, ExperimentalUI.NewUIComboBoxRenderer {

  private var listCellRendererParams: ListCellRendererParams<T>? = null

  private val selectablePanel = object : SelectablePanel(), KotlinUIDslRenderer {

    override fun getBaseline(width: Int, height: Int): Int {
      val patchedLabels = mutableListOf<Pair<JLabel, String>>()
      val layout = content.layout as GridLayout
      val baselineComponents = content.components
        .filter { layout.getConstraints(it as JComponent)!!.baselineAlign }

      // JLabel doesn't have baseline if empty. Workaround similar like in BasicComboBoxUI.getBaseline method
      for (component in baselineComponents) {
        if (component is JLabel && component.text.isNullOrEmpty()) {
          patchedLabels += component to component.text
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

      // Restore values
      for ((label, text) in patchedLabels) {
        label.text = text
      }
      return result
    }

    // Support disabled combobox color. Can be reworked later
    override fun setForeground(fg: Color?) {
      super.setForeground(fg)

      @Suppress("SENSELESS_COMPARISON")
      if (content == null) {
        // Called while initialization
        return
      }

      for (component in content.components) {
        component.foreground = fg
      }
    }

    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleJPanel() {
          override fun getAccessibleRole(): AccessibleRole = AccessibleRole.LABEL
        }
      }
      return accessibleContext
    }
  }

  /**
   * Content panel allows to trim components that could go outside of selection. It's better to implement that on layout level later
   */
  private val content = JPanel(GridLayout())
  private val cells = mutableListOf<LcrCellBaseImpl<*>>()
  private var gap = LcrRow.Gap.DEFAULT

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
    get() = if (ExperimentalUI.isNewUI() || !selected) selectablePanel.background else null
    set(value) {
      if (ExperimentalUI.isNewUI() || !selected) selectablePanel.background = value
    }

  override var selectionColor: Color?
    get() = if (selected) {
      if (ExperimentalUI.isNewUI()) selectablePanel.selectionColor else selectablePanel.background
    } else {
      null
    }
    set(value) {
      if (selected) {
        if (ExperimentalUI.isNewUI()) {
          selectablePanel.selectionColor = value
        }
        else {
          selectablePanel.background = value
        }
      }
    }

  private var foreground: Color = JBUI.CurrentTheme.List.FOREGROUND

  override fun gap(gap: LcrRow.Gap) {
    this.gap = gap
  }

  override fun icon(icon: Icon, init: (LcrIconInitParams.() -> Unit)?) {
    val initParams = LcrIconInitParams()
    initParams.accessibleName = (icon as? Accessible)?.accessibleContext?.accessibleName
    if (init != null) {
      initParams.init()
    }

    add(LcrIconImpl(initParams, false, gap, icon))
  }

  override fun text(text: @Nls String, init: (LcrTextInitParams.() -> Unit)?) {
    val initParams = LcrTextInitParams(foreground)
    initParams.accessibleName = text
    if (init != null) {
      initParams.init()
    }

    if (initParams.attributes == null) {
      add(LcrTextImpl(initParams, true, gap, text, selected, foreground))
    }
    else {
      add(LcrSimpleColoredTextImpl(initParams, true, gap, text, selected, foreground))
    }
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    cells.clear()
    content.removeAll()
    gap = LcrRow.Gap.DEFAULT

    val selectionBg = if (isSelected) RenderingUtil.getSelectionBackground(list) else null
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
            selectionInsets = JBInsets.create(0, 12)
            border = JBUI.Borders.empty(2, 20)
          }
          else {
            selectionInsets = JBInsets.create(0, 12)
            border = JBUI.Borders.empty(0, 20)
          }
          preferredHeight = JBUI.CurrentTheme.List.rowHeight()

          background = list.background
          selectionColor = selectionBg
        }
      }
    }
    else {
      selectablePanel.apply {
        background = selectionBg ?: list.background
        border = JBUI.Borders.empty(UIUtil.getListCellVPadding(), UIUtil.getListCellHPadding())
      }
    }

    listCellRendererParams = ListCellRendererParams(list, value, index, isSelected, cellHasFocus)
    foreground = if (selected) RenderingUtil.getSelectionForeground(list) else RenderingUtil.getForeground(list)

    renderer()

    // todo use row cache here
    val builder = RowsGridBuilder(content)
    builder.resizableRow()

    for (cell in cells) {
      val component = cell.type.createInstance()
      cell.apply(component)

      // Row height is usually even. If components height is odd the component cannot be placed right in center.
      // Because of rounding it's placed a little bit higher which looks not good, especially for text. This patch fixes that
      val roundingTopGapPatch = component.preferredSize.height % 2
      val gaps = UnscaledGaps(top = roundingTopGapPatch, left = if (builder.x == 0) 0 else getGapValue(cell.gapBefore))
      val horizontalAlign = when (cell.initParams.align) {
        null, LcrInitParams.Align.LEFT -> HorizontalAlign.LEFT
        LcrInitParams.Align.CENTER -> HorizontalAlign.CENTER
        LcrInitParams.Align.RIGHT -> HorizontalAlign.RIGHT
      }

      builder.cell(component, gaps = gaps,
                   horizontalAlign = horizontalAlign,
                   verticalAlign = VerticalAlign.CENTER,
                   resizableColumn = cell.initParams.align != null,
                   baselineAlign = cell.baselineAlign)
    }

    selectablePanel.accessibleContext.accessibleName = getAccessibleName()

    return selectablePanel
  }

  private fun add(lcrCell: LcrCellBaseImpl<*>) {
    cells.add(lcrCell)
    gap = LcrRow.Gap.DEFAULT
  }

  private fun getGapValue(gap: LcrRow.Gap): Int {
    return when (gap) {
      LcrRow.Gap.DEFAULT -> 4
      LcrRow.Gap.NONE -> 0
    }
  }

  private fun isComboBox(list: JList<*>): Boolean {
    return UIUtil.getParentOfType(BasicComboPopup::class.java, list) != null
  }

  @Nls
  private fun getAccessibleName(): String {
    val names = content.components
      .map { it.accessibleContext.accessibleName?.trim() }
      .filter { !it.isNullOrEmpty() }

    // Comma gives a good pause between unrelated text for readers on Windows and macOS
    return names.joinToString(", ")
  }
}

private data class ListCellRendererParams<T>(val list: JList<out T>,
                                             val value: T,
                                             val index: Int,
                                             val selected: Boolean,
                                             val hasFocus: Boolean)
