// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.*
import com.intellij.ui.popup.list.ComboBoxPopup
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.render.RenderingUtil
import com.intellij.util.ReflectionUtil
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

  companion object {
    const val DEFAULT_GAP = 6
  }

  private var listCellRendererParams: ListCellRendererParams<T>? = null

  private val rendererCache = RendererCache()
  private val cells = mutableListOf<LcrCellBaseImpl<*>>()
  private var separator: LcrSeparatorImpl? = null
  private var gap = LcrRow.Gap.DEFAULT

  override val list: JList<out T>
    get() = listCellRendererParams!!.list
  override val value: T
    get() = listCellRendererParams!!.value
  override val index: Int
    get() = listCellRendererParams!!.index
  override val selected: Boolean
    get() = listCellRendererParams!!.selected
  override val cellHasFocus: Boolean
    get() = listCellRendererParams!!.cellHasFocus

  override var background: Color? = null
  override var selectionColor: Color? = null

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

    add(LcrSimpleColoredTextImpl(initParams, true, gap, text, selected, foreground))
  }

  override fun separator(init: (LcrSeparator.() -> Unit)) {
    if (separator != null) {
      throw UiDslException("Separator is defined already")
    }

    val separator = LcrSeparatorImpl()
    separator.init()
    this.separator = separator
  }

  override fun getListCellRendererComponent(
    list: JList<out T>,
    value: T,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    cells.clear()
    separator = null
    gap = LcrRow.Gap.DEFAULT
    listCellRendererParams = ListCellRendererParams(list, value, index, isSelected, cellHasFocus)

    val renderingType = getRenderingType(list, index)
    // The list is not focused when isSwingPopup = false
    val isListFocused = renderingType.isComboBoxPopup() || RenderingUtil.isFocused(list)
    val selectionBg = if (isSelected) JBUI.CurrentTheme.List.Selection.background(isListFocused) else null
    val enabled: Boolean
    if (renderingType == RenderingType.COLLAPSED_SELECTED_COMBO_BOX_ITEM) {
      background = null
      selectionColor = null
      enabled = getComboBox(list)?.isEnabled ?: true
    }
    else {
      background = list.background
      selectionColor = selectionBg
      enabled = list.isEnabled
    }

    foreground = if (selected) JBUI.CurrentTheme.List.Selection.foreground(isListFocused) else RenderingUtil.getForeground(list)

    renderer()

    val cellsTypes = cells.map { it.type }
    val result = rendererCache.getRootPanel(cellsTypes)

    applyRowStyle(result, renderingType)

    result.listSeparator = if (separator == null) null else ListSeparator(separator!!.text)

    @Suppress("UNCHECKED_CAST")
    val model = list.model as? ListPopupModel<T>
    val listSeparator = when {
      model == null -> if (renderingType == RenderingType.COLLAPSED_SELECTED_COMBO_BOX_ITEM || separator == null) null
      else ListSeparator(separator!!.text)
      else -> if (model.isSeparatorAboveOf(value)) ListSeparator(model.getCaptionAboveOf(value)) else null
    }
    result.applySeparator(listSeparator, index == 0, list)

    for ((i, cell) in cells.withIndex()) {
      val component = result.applyCellConstraints(i, cell, if (i == 0) 0 else getGapValue(cell.beforeGap))
      cell.apply(component, enabled, list, isSelected)
    }

    return result
  }

  private fun applyRowStyle(rendererPanel: RendererPanel, renderingType: RenderingType) {
    if (ExperimentalUI.isNewUI()) {
      if (renderingType == RenderingType.COLLAPSED_SELECTED_COMBO_BOX_ITEM) {
        rendererPanel.initCollapsedComboBoxItem()
      }
      else {
        rendererPanel.initItem(background, if (selected) selectionColor else null)
      }
    }
    else {
      if (renderingType == RenderingType.COLLAPSED_SELECTED_COMBO_BOX_ITEM) {
        rendererPanel.initOldUICollapsedComboBoxItem()
      }
      else {
        rendererPanel.initOldUIItem(if (selected) selectionColor else background)
      }
    }
  }

  private fun add(lcrCell: LcrCellBaseImpl<*>) {
    cells.add(lcrCell)
    gap = LcrRow.Gap.DEFAULT
  }

  private fun getGapValue(gap: LcrRow.Gap): Int {
    return when (gap) {
      LcrRow.Gap.DEFAULT -> DEFAULT_GAP
      LcrRow.Gap.NONE -> 0
    }
  }

  private fun getRenderingType(list: JList<*>, index: Int): RenderingType {
    return when {
      index == -1 -> RenderingType.COLLAPSED_SELECTED_COMBO_BOX_ITEM
      list.getClientProperty(JBPopup.KEY) is ComboBoxPopup<*> -> RenderingType.IJ_COMBO_BOX_POPUP
      UIUtil.getParentOfType(BasicComboPopup::class.java, list) != null -> RenderingType.SWING_COMBO_BOX_POPUP
      else -> RenderingType.LIST
    }
  }

  /**
   * Uses reflection as a workaround, don't call frequently
   */
  private fun getComboBox(list: JList<*>): JComboBox<*>? {
    val popup = UIUtil.getParentOfType(BasicComboPopup::class.java, list) ?: return null
    try {
      val field = ReflectionUtil.findField(BasicComboPopup::class.java, JComboBox::class.java, "comboBox")
      return field.get(popup) as JComboBox<*>?
    }
    catch (e: ReflectiveOperationException) {
      return null
    }
  }
}

private data class ListCellRendererParams<T>(
  val list: JList<out T>,
  val value: T,
  val index: Int,
  val selected: Boolean,
  val cellHasFocus: Boolean,
)

/**
 * Unique key for different row configurations
 */
private data class RowKey(val types: List<LcrCellBaseImpl.Type>)

private class RendererCache {

  private val cache = HashMap<RowKey, RendererPanel>()

  fun getRootPanel(types: List<LcrCellBaseImpl.Type>): RendererPanel {
    val key = RowKey(types)
    return cache.getOrPut(key) {
      RendererPanel(key)
    }
  }
}

private class RendererPanel(key: RowKey) : JPanel(BorderLayout()), KotlinUIDslRendererComponent {

  private val cellsLayout = GridLayout()

  /**
   * Cells panel allows trimming components that could go outside selection
   */
  private val cellsPanel = JPanel(cellsLayout)

  private val selectablePanel = SelectablePanel()
  private val separator = GroupHeaderSeparator(if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Popup.separatorLabelInsets()
                                               else JBUI.insets(UIUtil.getListCellVPadding(), UIUtil.getListCellHPadding()))

  init {
    add(separator, BorderLayout.NORTH)
    add(selectablePanel, BorderLayout.CENTER)

    cellsPanel.isOpaque = false
    selectablePanel.layout = BorderLayout()
    selectablePanel.add(cellsPanel, BorderLayout.CENTER)

    val builder = RowsGridBuilder(cellsPanel)
    builder.resizableRow()
    for (type in key.types) {
      builder.cell(type.createInstance())
    }
  }

  override var listSeparator: ListSeparator? = null

  override fun getCopyText(): String? {
    // Find the first component with non-trivial text
    for (component in cellsPanel.components) {
      val result = when (component) {
        is SimpleColoredComponent -> component.getCharSequence(true).toString()
        is JLabel -> component.text // todo dead code?
        else -> throw UiDslException("Unsupported component type: ${component.javaClass.name}")
      }

      if (!result.isNullOrEmpty()) {
        return result
      }
    }

    return null
  }

  override fun getBaseline(width: Int, height: Int): Int {
    val patchedLabels = mutableListOf<Pair<JLabel, String>>()
    val baselineComponents = cellsPanel.components
      .filter { cellsLayout.getConstraints(it as JComponent)!!.baselineAlign }

    // JLabel doesn't have baseline if empty. Workaround similar like in BasicComboBoxUI.getBaseline method
    for (component in baselineComponents) {
      if (component is JLabel && component.text.isNullOrEmpty()) {
        patchedLabels += component to component.text
        component.text = " "
      }
    }
    selectablePanel.setSize(width, height)
    selectablePanel.doLayout()
    cellsPanel.doLayout()
    var result = -1
    for (component in baselineComponents) {
      val componentBaseline = component.getBaseline(component.width, component.height)
      if (componentBaseline >= 0) {
        result = max(result, cellsPanel.y + component.y + componentBaseline)
      }
    }

    // Restore values
    for ((label, text) in patchedLabels) {
      label.text = text
    }
    return result
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleJPanel() {
        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.LABEL
        override fun getAccessibleName(): String? {
          val names = cellsPanel.components
            .map { it.accessibleContext.accessibleName?.trim() }
            .filter { !it.isNullOrEmpty() }

          // Comma gives a good pause between unrelated text for readers on Windows and macOS
          return names.joinToString(", ")
        }
      }
    }
    return accessibleContext
  }

  fun applySeparator(listSeparator: ListSeparator?, isHideLine: Boolean, list: JList<*>) {
    separator.isVisible = listSeparator != null
    if (listSeparator != null) {
      separator.caption = listSeparator.text
      separator.isHideLine = isHideLine

      // Set background for separator
      background = list.background
    }
  }

  fun applyCellConstraints(i: Int, cell: LcrCellBaseImpl<*>, leftGap: Int): JComponent {
    val result = cellsPanel.getComponent(i) as JComponent
    val constraints = cellsLayout.getConstraints(result)!!

    // Row height is usually even. If components height is odd the component cannot be placed right in center.
    // Because of rounding it's placed a little bit higher which looks not good, especially for text. This patch fixes that
    val roundingTopGapPatch = result.preferredSize.height % 2
    val gaps = UnscaledGaps(top = roundingTopGapPatch, left = leftGap)
    val horizontalAlign = when (cell.initParams.align) {
      null, LcrInitParams.Align.LEFT -> HorizontalAlign.LEFT
      LcrInitParams.Align.CENTER -> HorizontalAlign.CENTER
      LcrInitParams.Align.RIGHT -> HorizontalAlign.RIGHT
    }
    val baselineAlign = cell.baselineAlign

    if (constraints.gaps != gaps || constraints.horizontalAlign != horizontalAlign || constraints.baselineAlign != baselineAlign) {
      val newConstrains = constraints.copy(gaps = gaps, horizontalAlign = horizontalAlign, baselineAlign = baselineAlign)
      cellsLayout.setComponentConstrains(result, newConstrains)
    }

    val resizableColumn = cell.initParams.align != null
    if (resizableColumn) {
      cellsLayout.rootGrid.resizableColumns += i
    }
    else {
      cellsLayout.rootGrid.resizableColumns -= i
    }

    return result
  }

  /**
   * Init renderer for selected item in collapsed ComboBox component
   */
  fun initCollapsedComboBoxItem() {
    with(selectablePanel) {
      isOpaque = false
      selectionArc = 0
      selectionInsets = JBInsets.emptyInsets()
      border = null
      preferredHeight = null
      background = null
      selectionColor = null
    }
  }

  fun initItem(background: Color?, selectionColor: Color?) {
    val leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get()
    val innerInsets = JBUI.CurrentTheme.Popup.Selection.innerInsets()

    with(selectablePanel) {
      // Update height/insets every time, so IDE scaling is applied
      isOpaque = true
      selectionArc = JBUI.CurrentTheme.Popup.Selection.ARC.get()
      selectionInsets = JBInsets.create(0, leftRightInset)
      border = JBUI.Borders.empty(0, innerInsets.left + leftRightInset, 0, innerInsets.right + leftRightInset)
      preferredHeight = JBUI.CurrentTheme.List.rowHeight()
      this.background = background
      this.selectionColor = selectionColor
    }
  }

  fun initOldUICollapsedComboBoxItem() {
    with(selectablePanel) {
      border = null
      background = null
      selectionColor = null
    }
  }

  fun initOldUIItem(background: Color?) {
    with(selectablePanel) {
      border = JBUI.Borders.empty(UIUtil.getListCellVPadding(), UIUtil.getListCellHPadding())
      this.background = background
      selectionColor = null
    }
  }
}

private enum class RenderingType {
  LIST,

  /**
   * [ComboBox.isSwingPopup] = true
   */
  SWING_COMBO_BOX_POPUP,

  /**
   * [ComboBox.isSwingPopup] = false
   */
  IJ_COMBO_BOX_POPUP,

  /**
   * index == -1: ComboBox is rendering the selected item in the collapsed state or for some technical purposes
   */
  COLLAPSED_SELECTED_COMBO_BOX_ITEM;

  fun isComboBoxPopup(): Boolean {
    return when (this) {
      SWING_COMBO_BOX_POPUP,
      IJ_COMBO_BOX_POPUP,
        -> true
      else -> false
    }
  }
}
