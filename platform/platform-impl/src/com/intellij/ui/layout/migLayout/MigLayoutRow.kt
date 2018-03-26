// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import net.miginfocom.layout.CC
import net.miginfocom.layout.ConstraintParser
import java.awt.Component
import javax.swing.*

private val LABEL_TOP_GAP = ConstraintParser.parseBoundSize("4px!", true, false)

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
        cc.vertical.gapBefore = gapToBoundSize(builder.largeVerticalGap, false)
        cc.vertical.gapAfter = gapToBoundSize(builder.verticalGap * 2, false)
        componentConstraints.put(separatorComponent, cc)
        separatorComponent()
      }
    }

    val row = MigLayoutRow(this, componentConstraints, builder, labeled = label != null, noGrid = noGrid, indent = indent + computeChildRowIndent(), buttonGroup = buttonGroup)
    subRows.add(row)

    if (label != null) {
      val labelComponentConstraints = CC()
      labelComponentConstraints.vertical.gapBefore = LABEL_TOP_GAP
      componentConstraints.put(label, labelComponentConstraints)
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
    if (components.isEmpty()) {
      return 0
    }
    else {
      val firstComponent = components.first()
      if (firstComponent is JRadioButton || firstComponent is JCheckBox) {
        return ComponentPanelBuilder.computeCommentInsets(firstComponent, true).left
      }
      else {
        return builder.horizontalGap * 3
      }
    }
  }

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

  override operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int, growPolicy: GrowPolicy?, comment: String?) {
    addComponent(this, constraints, gapLeft, growPolicy, comment)
  }

  // separate method to avoid JComponent as a receiver
  private fun addComponent(component: JComponent,
                           constraints: Array<out CCFlags>? = null,
                           gapLeft: Int = 0,
                           growPolicy: GrowPolicy? = null,
                           comment: String? = null) {
    components.add(component)

    if (comment != null && comment.isNotEmpty()) {
      gapAfter = "0px!"

      val isParentRowLabeled = labeled
      // create comment in a new sibling row (developer is still able to create sub rows because rows is not stored in a flat list)
      parent!!.createChildRow().apply {
        val commentComponent = ComponentPanelBuilder.createCommentComponent(comment, true)
        addComponent(commentComponent)

        val commentComponentCC = CC()
        commentComponentCC.horizontal.gapBefore = gapToBoundSize(ComponentPanelBuilder.computeCommentInsets(component, true).left, true)
        if (isParentRowLabeled) {
          commentComponentCC.skip()
        }
        componentConstraints.put(commentComponent, commentComponentCC)
      }
    }

    if (buttonGroup != null && component is JToggleButton) {
      buttonGroup.add(component)
    }

    val cc = constraints?.create()?.let { lazyOf(it) } ?: lazy { CC() }
    createComponentConstraints(cc, component, gapLeft = gapLeft, growPolicy = growPolicy)

    // JScrollPane doesn't have visual insets (not set by layout, but part of component implementation) as TextField, Combobox and other such components,
    // but it looks ugly, so, if not other components in the row and row is labeled - add left/right insets
    if (component is JScrollPane && labeled && components.size == 2) {
      cc.value.horizontal.gapBefore = gapToBoundSize(4, true)
      cc.value.horizontal.gapAfter = gapToBoundSize(4, true)
    }

    if (!noGrid && indent > 0 && components.size == 1) {
      cc.value.horizontal.gapBefore = gapToBoundSize(indent, true)
    }

    if (!shareCellWithPreviousComponentIfNeed(component, cc)) {
      // increase column index if cell mode not enabled or it is a first component of cell
      if (componentIndexWhenCellModeWasEnabled == -1 || componentIndexWhenCellModeWasEnabled == (components.size - 1)) {
        columnIndex++
      }
    }

    // if this row is not labeled and previous row is labeled and component is a "Remember" checkbox, skip one column (since this row doesn't have a label)
    if (!labeled && components.size == 1 && component is JCheckBox) {
      val siblings = parent!!.subRows
      if (siblings != null && siblings.size > 1 && siblings.get(siblings.size - 2).labeled && component.text == CommonBundle.message("checkbox.remember.password")) {
        cc.value.skip(1)
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
      componentCC.value.horizontal.gapBefore = gapToBoundSize(0, true)

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