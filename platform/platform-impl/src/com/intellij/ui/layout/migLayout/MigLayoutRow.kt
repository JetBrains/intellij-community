// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.VisualPaddingsProvider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import net.miginfocom.layout.CC
import java.awt.Component
import javax.swing.*
import javax.swing.border.LineBorder

internal class MigLayoutRow(private val parent: MigLayoutRow?,
                            private val componentConstraints: MutableMap<Component, CC>,
                            override val builder: MigLayoutBuilder,
                            val labeled: Boolean = false,
                            val noGrid: Boolean = false,
                            private val buttonGroup: ButtonGroup? = null,
                            private val indent: Int /* level number (nested rows) */) : Row() {
  val components = SmartList<JComponent>()
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

  override var enabled: Boolean = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      for (c in components) {
        c.isEnabled = value
      }
    }

  override var visible: Boolean = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      for (c in components) {
        c.isVisible = value
      }
    }

  override var subRowsEnabled: Boolean = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      subRows?.forEach { it.enabled = value }
    }

  override var subRowsVisible: Boolean = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      subRows?.forEach { it.visible = value }
    }

  internal val isLabeledIncludingSubRows: Boolean
    get() = labeled || (subRows?.any { it.isLabeledIncludingSubRows } ?: false)

  fun createChildRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null, separated: Boolean = false, noGrid: Boolean = false): MigLayoutRow {
    if (subRows == null) {
      subRows = SmartList()
    }

    val subRows = subRows!!

    if (separated) {
      val row = MigLayoutRow(this, componentConstraints, builder, indent = indent, noGrid = true)
      subRows.add(row)
      row.apply {
        val separatorComponent = SeparatorComponent(0, OnePixelDivider.BACKGROUND, null)
        val cc = CC()
        cc.vertical.gapBefore = gapToBoundSize(spacing.largeVerticalGap, false)
        cc.vertical.gapAfter = gapToBoundSize(spacing.verticalGap * 2, false)
        componentConstraints.put(separatorComponent, cc)
        addComponent(separatorComponent, lazyOf(cc))
      }
    }

    val row = MigLayoutRow(this, componentConstraints, builder,
                           labeled = label != null,
                           noGrid = noGrid,
                           indent = indent + computeChildRowIndent(),
                           buttonGroup = buttonGroup)
    subRows.add(row)

    if (label != null) {
      row.addComponent(label)
    }

    return row
  }

  // cell mode not tested with "gear" button, wait first user request
  override fun setCellMode(value: Boolean) {
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
        componentConstraints.getOrPut(component) { CC() }.split(components.size - firstComponentIndex)
      }
    }
  }

  private fun computeChildRowIndent(): Int {
    val firstComponent = components.firstOrNull() ?: return 0
    if (firstComponent is JRadioButton || firstComponent is JCheckBox) {
      return getCommentLeftInset(firstComponent)
    }
    else {
      return spacing.horizontalGap * 3
    }
  }

  private fun getCommentLeftInset(component: JComponent): Int {
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

  override operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int, growPolicy: GrowPolicy?, comment: String?) {
    addComponent(this, constraints.create()?.let { lazyOf(it) } ?: lazy { CC() }, gapLeft, growPolicy, comment)
  }

  // separate method to avoid JComponent as a receiver
  private fun addComponent(component: JComponent, cc: Lazy<CC> = lazy { CC() }, gapLeft: Int = 0, growPolicy: GrowPolicy? = null, comment: String? = null) {
    components.add(component)

    if (!shareCellWithPreviousComponentIfNeed(component, cc)) {
      // increase column index if cell mode not enabled or it is a first component of cell
      if (componentIndexWhenCellModeWasEnabled == -1 || componentIndexWhenCellModeWasEnabled == (components.size - 1)) {
        columnIndex++
      }
    }

    if (labeled && components.size == 2 && component.border is LineBorder) {
      componentConstraints.get(components.first())?.vertical?.gapBefore = builder.defaultComponentConstraintCreator.vertical1pxGap
    }

    // (not yet clear is it true or just some strange gaps from another source) MigLayout compensate outer visual paddings, but if there are more than one component in the cell,
    // inner horizontal spacing will be not corrected (e.g. between combobox and button will be 7px horizontal gap in case of macOS IntelliJ LaF), as solution, we set horizontal gap for such components).
    // if it is not a first component in the cell, compensate horizontal visual paddings using gap.
    if (componentIndexWhenCellModeWasEnabled != -1 && componentIndexWhenCellModeWasEnabled < (components.size - 1)) {
      cc.value.horizontal.gapBefore = gapToBoundSize(spacing.horizontalGap, true)
    }

    if (comment != null && comment.isNotEmpty()) {
      gapAfter = "${spacing.commentVerticalTopGap}px!"

      val isParentRowLabeled = labeled
      // create comment in a new sibling row (developer is still able to create sub rows because rows is not stored in a flat list)
      parent!!.createChildRow().apply {
        val commentComponent = ComponentPanelBuilder.createCommentComponent(comment, true)
        val commentComponentCC = CC()
        addComponent(commentComponent, lazyOf(commentComponentCC))
        commentComponentCC.horizontal.gapBefore = gapToBoundSize(getCommentLeftInset(component), true)
        if (isParentRowLabeled) {
          commentComponentCC.skip()
        }
        componentConstraints.put(commentComponent, commentComponentCC)
      }
    }

    if (buttonGroup != null && component is JToggleButton) {
      buttonGroup.add(component)
    }

    builder.defaultComponentConstraintCreator.createComponentConstraints(cc, component, gapLeft = gapLeft, growPolicy = growPolicy)

    if (!noGrid && indent > 0 && components.size == 1) {
      cc.value.horizontal.gapBefore = gapToBoundSize(indent, true)
    }

    // if this row is not labeled and previous row is labeled and component is a "Remember" checkbox, skip one column (since this row doesn't have a label)
    if (!labeled && components.size == 1 && component is JCheckBox) {
      val siblings = parent!!.subRows
      if (siblings != null && siblings.size > 1 && siblings.get(siblings.size - 2).labeled && component.text == CommonBundle.message("checkbox.remember.password")) {
        cc.value.skip(1)
      }
    }

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

    // "pushX can be used instead of having a "grow" keyword in the column/row constraints."
    // dealing with column constraints is tricky and not reliable since not easily to count correct column index for component,
    // approach like "set grow 0 for column #0 and 100 for other columns" leads to issues
    // when developer specifies pushX for component (MigLayout internally interprets it as grow for column) (because other column will have grow 100)
    // So - we just rely on MigLayout power and do not complicate our code

    // problem if we have component with push (e.g. ScrollPane) in a non-labeled row - to solve this problem (this label column starts to grow since latter component push cancel our push), we set noGrid if such component is the only in the row

    // so - we set 0 (actually, default AC() created with a one column constraint set to default, so, for first column size is 0 anyway) for labeled column, 100 if no component with pushX and 1000 if there is component with pushX
    if (cc.isInitialized() && cc.value.pushX != null) {
      if (columnIndex > 0) {
        // if pushX defined for component, set column grow to 1000 (value that greater than default non-labeled column grow)
        // (for now we don't allow to specify custom weight for push, so, real value of specified pushX doesn't matter)
        builder.columnConstraints.grow(1000f, columnIndex)
        // unset
        cc.value.pushX = null
      }
    }
    else if (columnIndex > 0 && columnIndex >= builder.columnConstraints.count) {
      // set default grow if not yet defined
      builder.columnConstraints.grow(100f, columnIndex)
    }
  }

  private fun shareCellWithPreviousComponentIfNeed(component: JComponent, componentCC: Lazy<CC>): Boolean {
    if (components.size > 1 && component is JLabel && component.icon === AllIcons.General.Gear) {
      componentCC.value.horizontal.gapBefore = builder.defaultComponentConstraintCreator.horizontalUnitSizeGap

      if (lastComponentConstraintsWithSplit == null) {
        val prevComponent = components.get(components.size - 2)!!
        var cc = componentConstraints.get(prevComponent)
        if (cc == null) {
          cc = CC()
          componentConstraints.set(prevComponent, cc)
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