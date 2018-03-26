// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.ui.components.noteComponent
import com.intellij.ui.layout.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.*
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JLabel

/**
 * Automatically add `growX` to JTextComponent (see isAddGrowX).
 * Automatically add `grow` and `push` to JPanel (see isAddGrowX).
 */
internal class MigLayoutBuilder : LayoutBuilderImpl {
  /**
   * Map of component to constraints shared among rows (since components are unique)
   */
  private val componentConstraints: MutableMap<Component, CC> = ContainerUtil.newIdentityTroveMap()
  private val rootRow = MigLayoutRow(parent = null, componentConstraints = componentConstraints, builder = this, indent = 0)

  val columnConstraints = AC()

  override fun newRow(label: JLabel?, buttonGroup: ButtonGroup?, separated: Boolean): Row {
    return rootRow.createChildRow(label = label, buttonGroup = buttonGroup, separated = separated)
  }

  override fun noteRow(text: String) {
    // add empty row as top gap
    newRow()

    val cc = CC()
    cc.vertical.gapBefore = gapToBoundSize(UIUtil.DEFAULT_VGAP, false)
    cc.vertical.gapAfter = gapToBoundSize(UIUtil.DEFAULT_VGAP * 2, false)

    val row = rootRow.createChildRow(label = null, noGrid = true)
    row.apply {
      val noteComponent = noteComponent(text)
      componentConstraints.put(noteComponent, cc)
      noteComponent()
    }
  }

  override fun build(container: Container, layoutConstraints: Array<out LCFlags>) {
    var gapTop = -1

    val lc = createLayoutConstraints()
    if (layoutConstraints.isEmpty()) {
      lc.fillX()
      // not fillY because it leads to enormously large cells - we use cc `push` in addition to cc `grow` as a more robust and easy solution
    }
    else {
      lc.apply(layoutConstraints)
    }

    lc.noVisualPadding()
    lc.hideMode = 3

    // if constraint specified only for rows 0 and 1, MigLayout will use constraint 1 for any rows with index 1+ (see LayoutUtil.getIndexSafe - use last element if index > size)
    val rowConstraints = AC()
    rowConstraints.align("top")
    container.layout = MigLayout(lc, columnConstraints, rowConstraints)

    val isNoGrid = layoutConstraints.contains(LCFlags.noGrid)

    var rowIndex = 0

    fun configureComponents(row: MigLayoutRow, prevRow: MigLayoutRow?) {
      val lastComponent = row.components.lastOrNull()
      if (lastComponent == null) {
        if (prevRow == null) {
          // do not add gap for the first row
          return
        }

        gapTop = UIUtil.LARGE_VGAP
      }

      for ((index, component) in row.components.withIndex()) {
        // MigLayout in any case always creates CC, so, create instance even if it is not required
        val cc = componentConstraints.get(component) ?: CC()

        if (gapTop != -1) {
          cc.vertical.gapBefore = gapToBoundSize(gapTop, false)
          gapTop = -1
        }

        if (isNoGrid) {
          container.add(component, cc)
          continue
        }

        if (component === lastComponent) {
          cc.spanX()
          cc.wrap()
        }

        if (row.noGrid) {
          if (component === row.components.first()) {
            rowConstraints.noGrid(rowIndex)
          }
        }
        else if (component === row.components.first()) {
          row.gapAfter?.let {
            rowConstraints.gap(it, rowIndex)
          }
        }

        if (index >= row.rightIndex) {
          cc.horizontal.gapBefore = BoundSize(null, null, null, true, null)
        }

        container.add(component, cc)
      }

      rowIndex++
    }

    fun processRows(rows: List<MigLayoutRow>) {
      var prevRow: MigLayoutRow? = null
      for (row in rows) {
        configureComponents(row, prevRow)
        row.subRows?.let {
          processRows(it)
        }

        prevRow = row
      }
    }

    rootRow.subRows?.let {
      processRows(it)
    }

    // do not hold components
    componentConstraints.clear()
  }
}

internal fun gapToBoundSize(value: Int, isHorizontal: Boolean): BoundSize {
  val unitValue = UnitValue(value.toFloat(), "px", isHorizontal, UnitValue.STATIC, null)
  return BoundSize(unitValue, unitValue, null, false, null)
}

// default values differs to MigLayout - IntelliJ Platform defaults are used
// gap multiplied by 2 (it seems in terms of MigLayout gap is both left and right space)
private fun createLayoutConstraints(gridGapX: Int = UIUtil.DEFAULT_HGAP * 2, gridGapY: Int = UIUtil.DEFAULT_VGAP): LC {
  val lc = LC()
  lc.gridGapX = gapToBoundSize(gridGapX, true)
  lc.gridGapY = gapToBoundSize(gridGapY, false)
  lc.insets = ConstraintParser.parseInsets("0px", true)
  return lc
}

private fun LC.apply(flags: Array<out LCFlags>): LC {
  for (flag in flags) {
    when (flag) {
      LCFlags.noGrid -> isNoGrid = true

      LCFlags.flowY -> isFlowX = false

      LCFlags.fill -> fill()
      LCFlags.fillX -> isFillX = true
      LCFlags.fillY -> isFillY = true

      LCFlags.lcWrap -> wrapAfter = 0

      LCFlags.debug -> debug()
    }
  }
  return this
}