// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.VisualPaddingsProvider
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.HideableTitledSeparator
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.LayoutUtil
import javax.swing.*
import javax.swing.border.LineBorder
import kotlin.math.max
import kotlin.reflect.KMutableProperty0

private const val COMPONENT_ENABLED_STATE_KEY = "MigLayoutRow.enabled"

internal class MigLayoutRow(private val parent: MigLayoutRow?,
                            override val builder: MigLayoutBuilder,
                            val labeled: Boolean = false,
                            val noGrid: Boolean = false,
                            private val indent: Int /* level number (nested rows) */,
                            private val incrementsIndent: Boolean = parent != null) : Row() {
  companion object {
    // as static method to ensure that members of current row are not used
    private fun createCommentRow(parent: MigLayoutRow,
                                 comment: String,
                                 maxLineLength: Int,
                                 indent: Int,
                                 isParentRowLabeled: Boolean,
                                 forComponent: Boolean,
                                 columnIndex: Int) {
      val cc = CC()
      val commentRow = parent.createChildRow()
      commentRow.isComment = true
      commentRow.addComponent(ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength), cc)
      if (forComponent) {
        cc.horizontal.gapBefore = BoundSize.NULL_SIZE
        cc.skip = columnIndex
      }
      else if (isParentRowLabeled) {
        cc.horizontal.gapBefore = BoundSize.NULL_SIZE
        cc.skip()
      }
      else {
        cc.horizontal.gapBefore = gapToBoundSize(indent + parent.spacing.indentLevel, true)
      }
    }

    // as static method to ensure that members of current row are not used
    private fun configureSeparatorRow(row: MigLayoutRow, title: String?) {
      val separatorComponent = if (title == null) SeparatorComponent(0, OnePixelDivider.BACKGROUND, null) else TitledSeparator(title)
      row.addTitleComponent(separatorComponent, isEmpty = title == null)
    }
  }

  val components: MutableList<JComponent> = SmartList()
  var rightIndex = Int.MAX_VALUE

  private var lastComponentConstraintsWithSplit: CC? = null

  private var columnIndex = -1

  internal var subRows: MutableList<MigLayoutRow>? = null
    private set

  var gapAfter: String? = null
    private set

  private var componentIndexWhenCellModeWasEnabled = -1

  private val spacing: SpacingConfiguration
    get() = builder.spacing

  private var isTrailingSeparator = false
  private var isComment = false

  override fun withButtonGroup(title: String?, buttonGroup: ButtonGroup, body: () -> Unit) {
    if (title != null) {
      label(title)
      gapAfter = "${spacing.radioGroupTitleVerticalGap}px!"
    }
    builder.withButtonGroup(buttonGroup, body)
  }

  override var enabled = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      for (c in components) {
        if (!value) {
          if (!c.isEnabled) {
            // current state of component differs from current row state - preserve current state to apply it when row state will be changed
            c.putClientProperty(COMPONENT_ENABLED_STATE_KEY, false)
          }
        }
        else {
          if (c.getClientProperty(COMPONENT_ENABLED_STATE_KEY) == false) {
            // remove because for active row component state can be changed and we don't want to add listener to update value accordingly
            c.putClientProperty(COMPONENT_ENABLED_STATE_KEY, null)
            // do not set to true, preserve old component state
            continue
          }
        }
        c.isEnabled = value
      }
    }

  override var visible = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      for (c in components) {
        c.isVisible = value
      }
    }

  override var subRowsEnabled = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      subRows?.forEach {
        it.enabled = value
        it.subRowsEnabled = value
      }
    }

  override var subRowsVisible = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      subRows?.forEach {
        it.visible = value
        it.subRowsVisible = value
      }
    }

  override var subRowIndent: Int = -1

  internal val isLabeledIncludingSubRows: Boolean
    get() = labeled || (subRows?.any { it.isLabeledIncludingSubRows } ?: false)

  internal val columnIndexIncludingSubRows: Int
    get() = max(columnIndex, subRows?.asSequence()?.map { it.columnIndexIncludingSubRows }?.max() ?: -1)

  override fun createChildRow(label: JLabel?, isSeparated: Boolean, noGrid: Boolean, title: String?): MigLayoutRow {
    return createChildRow(indent, label, isSeparated, noGrid, title)
  }

  private fun createChildRow(indent: Int,
                             label: JLabel? = null,
                             isSeparated: Boolean = false,
                             noGrid: Boolean = false,
                             title: String? = null,
                             incrementsIndent: Boolean = true): MigLayoutRow {
    val subRows = getOrCreateSubRowsList()
    val newIndent = if (!this.incrementsIndent) indent else indent + spacing.indentLevel

    val row = MigLayoutRow(this, builder,
                           labeled = label != null,
                           noGrid = noGrid,
                           indent = if (subRowIndent >= 0) subRowIndent * spacing.indentLevel else newIndent,
                           incrementsIndent = incrementsIndent)

    if (isSeparated) {
      val separatorRow = MigLayoutRow(this, builder, indent = newIndent, noGrid = true)
      configureSeparatorRow(separatorRow, title)
      separatorRow.enabled = subRowsEnabled
      separatorRow.subRowsEnabled = subRowsEnabled
      separatorRow.visible = subRowsVisible
      separatorRow.subRowsVisible = subRowsVisible
      row.getOrCreateSubRowsList().add(separatorRow)
    }

    var insertIndex = subRows.size
    if (insertIndex > 0 && subRows[insertIndex-1].isTrailingSeparator) {
      insertIndex--
    }
    if (insertIndex > 0 && subRows[insertIndex-1].isComment) {
      insertIndex--
    }
    subRows.add(insertIndex, row)

    row.enabled = subRowsEnabled
    row.subRowsEnabled = subRowsEnabled
    row.visible = subRowsVisible
    row.subRowsVisible = subRowsVisible

    if (label != null) {
      row.addComponent(label)
    }

    return row
  }

  private fun <T : JComponent> addTitleComponent(titleComponent: T, isEmpty: Boolean) {
    val cc = CC().apply {
      if (isEmpty) {
        vertical.gapAfter = gapToBoundSize(spacing.verticalGap * 2, false)
        isTrailingSeparator = true
      }
      else {
        // TitledSeparator doesn't grow by default opposite to SeparatorComponent
        growX()
      }
    }
    addComponent(titleComponent, cc)
  }

  override fun titledRow(title: String, init: Row.() -> Unit): Row {
    return createBlockRow(title, true, init)
  }

  override fun blockRow(init: Row.() -> Unit): Row {
    return createBlockRow(null, false, init)
  }

  private fun createBlockRow(title: String?, isSeparated: Boolean, init: Row.() -> Unit): Row {
    return createChildRow(indent = indent, title = title, isSeparated = isSeparated, incrementsIndent = isSeparated)
      .apply(init)
      .apply {
        createChildRow().apply {
          placeholder()
          largeGapAfter()
        }
      }
  }

  override fun hideableRow(title: String, init: Row.() -> Unit): Row {
    val titledSeparator = HideableTitledSeparator(title)
    val separatorRow = createChildRow()
    separatorRow.addTitleComponent(titledSeparator, isEmpty = false)
    builder.hideableRowNestingLevel++
    try {
      val panelRow = createChildRow(indent + spacing.indentLevel).apply(init)
      titledSeparator.row = panelRow
      titledSeparator.collapse()
      return panelRow
    }
    finally {
      builder.hideableRowNestingLevel--
    }
  }

  private fun getOrCreateSubRowsList(): MutableList<MigLayoutRow> {
    var subRows = subRows
    if (subRows == null) {
      // subRows in most cases > 1
      subRows = ArrayList()
      this.subRows = subRows
    }
    return subRows
  }

  // cell mode not tested with "gear" button, wait first user request
  override fun setCellMode(value: Boolean, isVerticalFlow: Boolean, fullWidth: Boolean) {
    if (value) {
      assert(componentIndexWhenCellModeWasEnabled == -1)
      componentIndexWhenCellModeWasEnabled = components.size
    }
    else {
      val firstComponentIndex = componentIndexWhenCellModeWasEnabled
      componentIndexWhenCellModeWasEnabled = -1
      // do not add split if cell empty or contains the only component
      if ((components.size - firstComponentIndex) > 1) {
        val component = components.get(firstComponentIndex)
        val cc = component.constraints
        cc.split(components.size - firstComponentIndex)
        if (fullWidth) {
          cc.spanX(LayoutUtil.INF)
        }
        if (isVerticalFlow) {
          cc.flowY()
          // because when vertical buttons placed near scroll pane, it wil be centered by baseline (and baseline not applicable for grow elements, so, will be centered)
          cc.alignY("top")
        }
      }
    }
  }

  override fun <T : JComponent> component(component: T): CellBuilder<T> {
    addComponent(component)
    return CellBuilderImpl(builder, this, component)
  }

  internal fun addComponent(component: JComponent, cc: CC = CC()) {
    components.add(component)
    builder.componentConstraints.put(component, cc)

    if (!visible) {
      component.isVisible = false
    }
    if (!enabled) {
      component.isEnabled = false
    }

    if (!shareCellWithPreviousComponentIfNeeded(component, cc)) {
      // increase column index if cell mode not enabled or it is a first component of cell
      if (componentIndexWhenCellModeWasEnabled == -1 || componentIndexWhenCellModeWasEnabled == (components.size - 1)) {
        columnIndex++
      }
    }

    if (labeled && components.size == 2 && component.border is LineBorder) {
      builder.componentConstraints.get(components.first())?.vertical?.gapBefore = builder.defaultComponentConstraintCreator.vertical1pxGap
    }

    if (component is JRadioButton) {
      builder.topButtonGroup?.add(component)
    }

    builder.defaultComponentConstraintCreator.addGrowIfNeeded(cc, component, spacing)

    if (!noGrid && indent > 0 && components.size == 1) {
      cc.horizontal.gapBefore = gapToBoundSize(indent, true)
    }

    if (builder.hideableRowNestingLevel > 0) {
      cc.hideMode = 0
    }

    // if this row is not labeled and:
    // a. previous row is labeled and first component is a "Remember" checkbox, skip one column (since this row doesn't have a label)
    // b. some previous row is labeled and first component is a checkbox, span (since this checkbox should span across label and content cells)
    if (!labeled && components.size == 1 && component is JCheckBox) {
      val siblings = parent!!.subRows
      if (siblings != null && siblings.size > 1) {
        if (siblings.get(siblings.size - 2).labeled && component.text == UIBundle.message("auth.remember.cb")) {
          cc.skip(1)
          cc.horizontal.gapBefore = BoundSize.NULL_SIZE
        }
        else if (siblings.any { it.labeled }) {
          cc.spanX(2)
        }
      }
    }

    // MigLayout doesn't check baseline if component has grow
    if (labeled && component is JScrollPane && component.viewport.view is JTextArea) {
      val labelCC = components.get(0).constraints
      labelCC.alignY("top")

      val labelTop = component.border?.getBorderInsets(component)?.top ?: 0
      if (labelTop != 0) {
        labelCC.vertical.gapBefore = gapToBoundSize(labelTop, false)
      }
    }
  }

  private val JComponent.constraints get() = builder.componentConstraints.getOrPut(this) { CC() }

  fun addCommentRow(component: JComponent, comment: String, maxLineLength: Int, forComponent: Boolean) {
    gapAfter = "${spacing.commentVerticalTopGap}px!"

    val isParentRowLabeled = labeled
    createCommentRow(this, comment, maxLineLength, indent, isParentRowLabeled, forComponent, columnIndex)
  }

  private fun shareCellWithPreviousComponentIfNeeded(component: JComponent, componentCC: CC): Boolean {
    if (components.size > 1 && component is JLabel && component.icon === AllIcons.General.GearPlain) {
      componentCC.horizontal.gapBefore = builder.defaultComponentConstraintCreator.horizontalUnitSizeGap

      if (lastComponentConstraintsWithSplit == null) {
        val prevComponent = components.get(components.size - 2)
        val prevCC = prevComponent.constraints
        prevCC.split++
        lastComponentConstraintsWithSplit = prevCC
      }
      else {
        lastComponentConstraintsWithSplit!!.split++
      }
      return true
    }
    else {
      lastComponentConstraintsWithSplit = null
      return false
    }
  }

  override fun alignRight() {
    if (rightIndex != Int.MAX_VALUE) {
      throw IllegalStateException("right allowed only once")
    }
    rightIndex = components.size
  }

  override fun largeGapAfter() {
    gapAfter = "${spacing.largeVerticalGap}px!"
  }

  override fun createRow(label: String?): Row {
    return createChildRow(label = label?.let { Label(it) })
  }

  override fun createNoteOrCommentRow(component: JComponent): Row {
    val cc = CC()
    cc.vertical.gapBefore = gapToBoundSize(if (subRows == null) spacing.verticalGap else spacing.largeVerticalGap, false)
    cc.vertical.gapAfter = gapToBoundSize(spacing.verticalGap, false)

    val row = createChildRow(label = null, noGrid = true)
    row.addComponent(component, cc)
    return row
  }

  override fun radioButton(text: String, comment: String?): CellBuilder<JBRadioButton> {
    return super.radioButton(text, comment).also { attachSubRowsEnabled(it.component) }
  }

  override fun radioButton(text: String, prop: KMutableProperty0<Boolean>, comment: String?): CellBuilder<JBRadioButton> {
    return super.radioButton(text, prop, comment).also { attachSubRowsEnabled(it.component) }
  }

  override fun onGlobalApply(callback: () -> Unit): Row = apply {
    builder.applyCallbacks.putValue(null, callback)
  }

  override fun onGlobalReset(callback: () -> Unit): Row = apply {
    builder.resetCallbacks.putValue(null, callback)
  }

  override fun onGlobalIsModified(callback: () -> Boolean): Row = apply {
    builder.isModifiedCallbacks.putValue(null, callback)
  }
}

class CellBuilderImpl<T : JComponent> internal constructor(
  private val builder: MigLayoutBuilder,
  private val row: MigLayoutRow,
  override val component: T
) : CellBuilder<T>, CheckboxCellBuilder, ScrollPaneCellBuilder {
  private var applyIfEnabled = false
  private var property: GraphProperty<*>? = null

  override fun withGraphProperty(property: GraphProperty<*>): CellBuilder<T> {
    this.property = property
    return this
  }

  override fun comment(text: String, maxLineLength: Int): CellBuilder<T> {
    row.addCommentRow(component, text, maxLineLength, forComponent = false)
    return this
  }

  override fun commentComponent(text: String, maxLineLength: Int): CellBuilder<T> {
    row.addCommentRow(component, text, maxLineLength, forComponent = true)
    return this
  }

  override fun focused(): CellBuilder<T> {
    builder.preferredFocusedComponent = component
    return this
  }

  override fun withValidationOnApply(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T> {
    builder.validateCallbacks.add { callback(ValidationInfoBuilder(component.origin), component) }
    return this
  }

  override fun withValidationOnInput(callback: ValidationInfoBuilder.(T) -> ValidationInfo?): CellBuilder<T> {
    builder.componentValidateCallbacks[component.origin] = { callback(ValidationInfoBuilder(component.origin), component) }
    property?.let { builder.customValidationRequestors.putValue(component.origin, it::afterPropagation) }
    return this
  }

  override fun onApply(callback: () -> Unit): CellBuilder<T> {
    builder.applyCallbacks.putValue(component, callback)
    return this
  }

  override fun onReset(callback: () -> Unit): CellBuilder<T> {
    builder.resetCallbacks.putValue(component, callback)
    return this
  }

  override fun onIsModified(callback: () -> Boolean): CellBuilder<T> {
    builder.isModifiedCallbacks.putValue(component, callback)
    return this
  }

  override fun enabled(isEnabled: Boolean) {
    component.isEnabled = isEnabled
  }

  override fun enableIf(predicate: ComponentPredicate): CellBuilder<T> {
    component.isEnabled = predicate()
    predicate.addListener { component.isEnabled = it }
    return this
  }

  override fun applyIfEnabled(): CellBuilder<T> {
    applyIfEnabled = true
    return this
  }

  override fun shouldSaveOnApply(): Boolean {
    if (applyIfEnabled && !component.isEnabled) return false
    return true
  }

  override fun actsAsLabel() {
    builder.updateComponentConstraints(component) { spanX = 1 }
  }

  override fun noGrowY() {
    builder.updateComponentConstraints(component) {
      growY(0.0f)
      pushY(0.0f)
    }
  }

  override fun sizeGroup(name: String) = apply {
    builder.updateComponentConstraints(component) {
      sizeGroup(name)
    }
  }

  override fun growPolicy(growPolicy: GrowPolicy): CellBuilder<T> = apply {
    builder.updateComponentConstraints(component) {
      builder.defaultComponentConstraintCreator.applyGrowPolicy(this, growPolicy)
    }
  }

  override fun constraints(vararg constraints: CCFlags): CellBuilder<T> = apply {
    builder.updateComponentConstraints(component) {
      overrideFlags(this, constraints)
    }
  }

  override fun withLargeLeftGap(): CellBuilder<T> = apply {
    builder.updateComponentConstraints(component) {
      horizontal.gapBefore = gapToBoundSize(builder.spacing.largeHorizontalGap, true)
    }
  }

  override fun withLeftGap(gapLeft: Int): CellBuilder<T> = apply {
    builder.updateComponentConstraints(component) {
      horizontal.gapBefore = gapToBoundSize(gapLeft, true)
    }
  }
}

private fun getCommentLeftInset(component: JComponent): Int {
  if (component is JTextField) {
    // 1px border, better to indent comment text
    return 1
  }

  // as soon as ComponentPanelBuilder will also compensate visual paddings (instead of compensating on LaF level),
  // this logic will be moved into computeCommentInsets
  val border = component.border
  val componentBorderVisualLeftPadding =
    if (border is VisualPaddingsProvider) {
      border.getVisualPaddings(component)?.left ?: 0
    }
    else {
      0
    }

  val insets = ComponentPanelBuilder.computeCommentInsets(component, true)
  return insets.left - componentBorderVisualLeftPadding
}

private val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }