// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.VisualPaddingsProvider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import net.miginfocom.layout.CC
import java.awt.Component
import javax.swing.*
import javax.swing.border.LineBorder
import kotlin.reflect.KProperty0

private const val COMPONENT_ENABLED_STATE_KEY = "MigLayoutRow.enabled"

internal class MigLayoutRow(private val parent: MigLayoutRow?,
                            private val componentConstraints: MutableMap<Component, CC>,
                            override val builder: MigLayoutBuilder,
                            val labeled: Boolean = false,
                            val noGrid: Boolean = false,
                            private val buttonGroup: ButtonGroup? = null,
                            private val indent: Int /* level number (nested rows) */) : Row() {
  companion object {
    // as static method to ensure that members of current row are not used
    private fun createCommentRow(parent: MigLayoutRow, comment: String, component: JComponent, isParentRowLabeled: Boolean) {
      val cc = CC()
      parent.createChildRow().addComponent(ComponentPanelBuilder.createCommentComponent(comment, true), lazyOf(cc))
      cc.horizontal.gapBefore = gapToBoundSize(getCommentLeftInset(parent.spacing, component), true)
      if (isParentRowLabeled) {
        cc.skip()
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
      subRows?.forEach { it.enabled = value }
    }

  override var subRowsVisible = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      subRows?.forEach { it.visible = value }
    }

  internal val isLabeledIncludingSubRows: Boolean
    get() = labeled || (subRows?.any { it.isLabeledIncludingSubRows } ?: false)

  internal val columnIndexIncludingSubRows: Int
    get() = Math.max(columnIndex, subRows?.maxBy { it.columnIndex }?.columnIndex ?: 0)

  fun createChildRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null, isSeparated: Boolean = false, noGrid: Boolean = false, title: String? = null): MigLayoutRow {
    val subRows = getOrCreateSubRowsList()

    val row = MigLayoutRow(this, componentConstraints, builder,
                           labeled = label != null,
                           noGrid = noGrid,
                           indent = indent + computeChildRowIndent(),
                           buttonGroup = buttonGroup)

    if (isSeparated) {
      val separatorRow = MigLayoutRow(this, componentConstraints, builder, indent = indent, noGrid = true)
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
  override fun setCellMode(value: Boolean, isVerticalFlow: Boolean) {
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
        val cc = componentConstraints.getOrPut(component) { CC() }
        cc.split(components.size - firstComponentIndex)
        if (isVerticalFlow) {
          cc.flowY()
          // because when vertical buttons placed near scroll pane, it wil be centered by baseline (and baseline not applicable for grow elements, so, will be centered)
          cc.alignY("top")
        }
      }
    }
  }

  private fun computeChildRowIndent(): Int {
    val firstComponent = components.firstOrNull() ?: return 0
    if (firstComponent is JRadioButton || firstComponent is JCheckBox) {
      return getCommentLeftInset(spacing, firstComponent)
    }
    else {
      return spacing.horizontalGap * 3
    }
  }

  override operator fun <T : JComponent> T.invoke(vararg constraints: CCFlags, gapLeft: Int, growPolicy: GrowPolicy?, comment: String?): CellBuilder<T> {
    addComponent(this, constraints.create()?.let { lazyOf(it) } ?: lazy { CC() }, gapLeft, growPolicy, comment)
    return CellBuilderImpl(builder, this)
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

    if (!shareCellWithPreviousComponentIfNeed(component, cc)) {
      // increase column index if cell mode not enabled or it is a first component of cell
      if (componentIndexWhenCellModeWasEnabled == -1 || componentIndexWhenCellModeWasEnabled == (components.size - 1)) {
        columnIndex++
      }
    }

    if (labeled && components.size == 2 && component.border is LineBorder) {
      componentConstraints.get(components.first())?.vertical?.gapBefore = builder.defaultComponentConstraintCreator.vertical1pxGap
    }

    if (comment != null && comment.isNotEmpty()) {
      gapAfter = "${spacing.commentVerticalTopGap}px!"

      val isParentRowLabeled = labeled
      // create comment in a new sibling row (developer is still able to create sub rows because rows is not stored in a flat list)
      createCommentRow(parent!!, comment, component, isParentRowLabeled)
    }

    if (buttonGroup != null && component is JToggleButton) {
      buttonGroup.add(component)
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
        if (siblings.get(siblings.size - 2).labeled && component.text == CommonBundle.message("checkbox.remember.password")) {
          cc.value.skip(1)
        }
        else if (siblings.any { it.labeled }) {
          cc.value.spanX(2)
        }
      }
    }

    // MigLayout doesn't check baseline if component has grow
    if (labeled && component is JScrollPane && component.viewport.view is JTextArea) {
      val labelCC = componentConstraints.getOrPut(components.get(0)) { CC() }
      labelCC.alignY("top")

      val labelTop = component.border?.getBorderInsets(component)?.top ?: 0
      if (labelTop != 0) {
        labelCC.vertical.gapBefore = gapToBoundSize(labelTop, false)
      }
    }

    if (cc.isInitialized()) {
      componentConstraints.put(component, cc.value)
    }
  }

  private fun shareCellWithPreviousComponentIfNeed(component: JComponent, componentCC: Lazy<CC>): Boolean {
    if (components.size > 1 && component is JLabel && component.icon === AllIcons.General.GearPlain) {
      componentCC.value.horizontal.gapBefore = builder.defaultComponentConstraintCreator.horizontalUnitSizeGap

      if (lastComponentConstraintsWithSplit == null) {
        val prevComponent = components.get(components.size - 2)
        var cc = componentConstraints.get(prevComponent)
        if (cc == null) {
          cc = CC()
          componentConstraints.put(prevComponent, cc)
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

  override fun createRow(label: String?): Row {
    return createChildRow(label = label?.let { Label(it) })
  }
}

class CellBuilderImpl<T : JComponent> internal constructor(
  private val builder: MigLayoutBuilder,
  override val component: T
) : CellBuilder<T> {
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

  override fun enabled(isEnabled: Boolean) {
    component.isEnabled = isEnabled
  }

  override fun enableIfSelected(button: AbstractButton) {
    component.isEnabled = button.isSelected
    button.addChangeListener { component.isEnabled = button.isSelected }
  }
}

private fun getCommentLeftInset(spacing: SpacingConfiguration, component: JComponent): Int {
  if (component is JTextField) {
    // 1px border, better to indent comment text
    return 1
  }

  // as soon as ComponentPanelBuilder will also compensate visual paddings (instead of compensating on LaF level),
  // this logic will be moved into computeCommentInsets
  val componentBorderVisualLeftPadding = when {
    spacing.isCompensateVisualPaddings -> {
      val border = component.border
      if (border is VisualPaddingsProvider) {
        border.getVisualPaddings(component)?.left ?: 0
      }
      else {
        0
      }
    }
    else -> 0
  }
  val insets = ComponentPanelBuilder.computeCommentInsets(component, true)
  return insets.left - componentBorderVisualLeftPadding
}