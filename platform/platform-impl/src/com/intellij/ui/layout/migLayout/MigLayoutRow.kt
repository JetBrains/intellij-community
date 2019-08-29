// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.VisualPaddingsProvider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.UIBundle
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.LayoutUtil
import javax.swing.*
import javax.swing.border.LineBorder

private const val COMPONENT_ENABLED_STATE_KEY = "MigLayoutRow.enabled"

internal class MigLayoutRow(private val parent: MigLayoutRow?,
                            override val builder: MigLayoutBuilder,
                            val labeled: Boolean = false,
                            val noGrid: Boolean = false,
                            private val indent: Int /* level number (nested rows) */) : Row() {
  companion object {
    // as static method to ensure that members of current row are not used
    private fun createCommentRow(parent: MigLayoutRow, comment: String, component: JComponent, indent: Int, isParentRowLabeled: Boolean, maxLineLength: Int) {
      val cc = CC()
      parent.createChildRow().addComponent(ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength), lazyOf(cc))
      if (isParentRowLabeled) {
        cc.horizontal.gapBefore = BoundSize.NULL_SIZE
        cc.skip()
      }
      else {
        cc.horizontal.gapBefore = gapToBoundSize(getCommentLeftInset(component) + indent, true)
      }
    }

    // as static method to ensure that members of current row are not used
    private fun configureSeparatorRow(row: MigLayoutRow, title: String?) {
      val separatorComponent = if (title == null) SeparatorComponent(0, OnePixelDivider.BACKGROUND, null) else TitledSeparator(title)
      val cc = CC()
      val spacing = row.spacing
      cc.vertical.gapBefore = gapToBoundSize(spacing.largeVerticalGap, false)
      if (title == null) {
        cc.vertical.gapAfter = gapToBoundSize(spacing.verticalGap * 2, false)
      }
      else {
        cc.vertical.gapAfter = gapToBoundSize(spacing.verticalGap, false)
        // TitledSeparator doesn't grow by default opposite to SeparatorComponent
        cc.growX()
      }
      row.addComponent(separatorComponent, lazyOf(cc))
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

  override fun withButtonGroup(buttonGroup: ButtonGroup, body: () -> Unit) {
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

  internal val isLabeledIncludingSubRows: Boolean
    get() = labeled || (subRows?.any { it.isLabeledIncludingSubRows } ?: false)

  internal val columnIndexIncludingSubRows: Int
    get() = Math.max(columnIndex, subRows?.maxBy { it.columnIndex }?.columnIndex ?: 0)

  override fun createChildRow(label: JLabel?, isSeparated: Boolean, noGrid: Boolean, title: String?): MigLayoutRow {
    val subRows = getOrCreateSubRowsList()

    val row = MigLayoutRow(this, builder,
                           labeled = label != null,
                           noGrid = noGrid,
                           indent = indent + computeChildRowIndent(isSeparated))

    if (isSeparated) {
      val separatorRow = MigLayoutRow(this, builder, indent = indent, noGrid = true)
      configureSeparatorRow(separatorRow, title)
      separatorRow.enabled = subRowsEnabled
      separatorRow.visible = subRowsVisible
      row.getOrCreateSubRowsList().add(separatorRow)
    }

    subRows.add(row)
    row.enabled = subRowsEnabled
    row.visible = subRowsVisible

    if (label != null) {
      row.addComponent(label)
    }

    return row
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
        val cc = builder.componentConstraints.getOrPut(component) { CC() }
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

  private fun computeChildRowIndent(isSeparated: Boolean): Int {
    if (isSeparated) {
      return spacing.indentLevel
    }
    val firstComponent = components.firstOrNull() ?: return 0
    if (firstComponent is JRadioButton || firstComponent is JCheckBox) {
      return getCommentLeftInset(firstComponent)
    }
    else {
      return spacing.indentLevel
    }
  }

  override operator fun <T : JComponent> T.invoke(vararg constraints: CCFlags, gapLeft: Int, growPolicy: GrowPolicy?, comment: String?): CellBuilder<T> {
    addComponent(this, constraints.create()?.let { lazyOf(it) } ?: lazy { CC() }, gapLeft, growPolicy, comment)
    return CellBuilderImpl(builder, this@MigLayoutRow, this)
  }

  // separate method to avoid JComponent as a receiver
  internal fun addComponent(component: JComponent, cc: Lazy<CC> = lazy { CC() }, gapLeft: Int = 0, growPolicy: GrowPolicy? = null, comment: String? = null) {
    components.add(component)

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

    if (comment != null && comment.isNotEmpty()) {
      addCommentRow(component, comment)
    }

    if (component is JRadioButton) {
      builder.topButtonGroup?.add(component)
    }

    builder.defaultComponentConstraintCreator.createComponentConstraints(cc, component, gapLeft = gapLeft, growPolicy = growPolicy)

    if (!noGrid && indent > 0 && components.size == 1) {
      cc.value.horizontal.gapBefore = gapToBoundSize(indent, true)
    }

    // if this row is not labeled and:
    // a. previous row is labeled and first component is a "Remember" checkbox, skip one column (since this row doesn't have a label)
    // b. some previous row is labeled and first component is a checkbox, span (since this checkbox should span across label and content cells)
    if (!labeled && components.size == 1 && component is JCheckBox) {
      val siblings = parent!!.subRows
      if (siblings != null && siblings.size > 1) {
        if (siblings.get(siblings.size - 2).labeled && component.text == UIBundle.message("auth.remember.cb")) {
          cc.value.skip(1)
          cc.value.horizontal.gapBefore = BoundSize.NULL_SIZE
        }
        else if (siblings.any { it.labeled }) {
          cc.value.spanX(2)
        }
      }
    }

    // MigLayout doesn't check baseline if component has grow
    if (labeled && component is JScrollPane && component.viewport.view is JTextArea) {
      val labelCC = builder.componentConstraints.getOrPut(components.get(0)) { CC() }
      labelCC.alignY("top")

      val labelTop = component.border?.getBorderInsets(component)?.top ?: 0
      if (labelTop != 0) {
        labelCC.vertical.gapBefore = gapToBoundSize(labelTop, false)
      }
    }

    if (cc.isInitialized()) {
      builder.componentConstraints.put(component, cc.value)
    }
  }

  fun addCommentRow(component: JComponent, comment: String, maxLineLength: Int = 70) {
    gapAfter = "${spacing.commentVerticalTopGap}px!"

    val isParentRowLabeled = labeled
    // create comment in a new sibling row (developer is still able to create sub rows because rows is not stored in a flat list)
    createCommentRow(parent!!, comment, component, indent, isParentRowLabeled, maxLineLength)
  }

  private fun shareCellWithPreviousComponentIfNeeded(component: JComponent, componentCC: Lazy<CC>): Boolean {
    if (components.size > 1 && component is JLabel && component.icon === AllIcons.General.GearPlain) {
      componentCC.value.horizontal.gapBefore = builder.defaultComponentConstraintCreator.horizontalUnitSizeGap

      if (lastComponentConstraintsWithSplit == null) {
        val prevComponent = components.get(components.size - 2)
        var cc = builder.componentConstraints.get(prevComponent)
        if (cc == null) {
          cc = CC()
          builder.componentConstraints.put(prevComponent, cc)
        }
        cc.split++
        lastComponentConstraintsWithSplit = cc
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
    gapAfter = "${spacing.largeVerticalGap * 2}px!"
  }

  override fun createRow(label: String?): Row {
    return createChildRow(label = label?.let { Label(it) })
  }

  override fun createNoteOrCommentRow(component: JComponent): Row {
    val cc = CC()
    cc.vertical.gapBefore = gapToBoundSize(if (subRows == null) spacing.verticalGap else spacing.largeVerticalGap, false)
    cc.vertical.gapAfter = gapToBoundSize(spacing.verticalGap, false)

    val row = createChildRow(label = null, noGrid = true)
    row.addComponent(component, lazyOf(cc))
    return row
  }
}

class CellBuilderImpl<T : JComponent> internal constructor(
  private val builder: MigLayoutBuilder,
  private val row: MigLayoutRow,
  override val component: T
) : CellBuilder<T>, CheckboxCellBuilder {
  override fun comment(text: String, maxLineLength: Int): CellBuilder<T> {
    row.addCommentRow(component, text, maxLineLength)
    return this
  }

  override fun focused(): CellBuilder<T> {
    builder.preferredFocusedComponent = component
    return this
  }

  override fun withValidation(callback: (T) -> ValidationInfo?): CellBuilder<T> {
    builder.validateCallbacks.add { callback(component) }
    return this
  }

  override fun onApply(callback: () -> Unit): CellBuilder<T> {
    builder.applyCallbacks.add(callback)
    return this
  }

  override fun onReset(callback: () -> Unit): CellBuilder<T> {
    builder.resetCallbacks.add(callback)
    return this
  }

  override fun onIsModified(callback: () -> Boolean): CellBuilder<T> {
    builder.isModifiedCallbacks.add(callback)
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

  override fun actsAsLabel() {
    builder.updateComponentConstraints(component) { spanX = 1 }
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