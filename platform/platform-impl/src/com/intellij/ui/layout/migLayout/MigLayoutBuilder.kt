// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.icons.AllIcons
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

    val columnConstraints = AC()
    var columnIndex = 0
    val rowConstraints = AC()
    rowConstraints.align("top")
    container.layout = MigLayout(lc, columnConstraints, rowConstraints)

    val isNoGrid = layoutConstraints.contains(LCFlags.noGrid)

    var rowIndex = 0

    fun configureComponents(row: MigLayoutRow, prevRow: MigLayoutRow?, isLabeled: Boolean) {
      val lastComponent = row.components.lastOrNull()
      if (lastComponent == null) {
        if (prevRow == null) {
          // do not add gap for the first row
          return
        }

        // https://goo.gl/LDylKm
        // gap = 10u where u = 4px
        gapTop = UIUtil.DEFAULT_VGAP * 3
      }

      var isSplitRequired = true
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
          isSplitRequired = false
          cc.wrap()

          if (isLabeled) {
            columnConstraints.grow(100f, columnIndex++)
          }
        }

        if (row.noGrid) {
          if (component === row.components.first()) {
            // rowConstraints.noGrid() doesn't work correctly
            cc.spanX()
          }
        }
        else {
          var isSkippableComponent = true
          if (component === row.components.first()) {
            row.gapAfter?.let {
              rowConstraints.gap(it, rowIndex)
            }

            if (isLabeled) {
              if (row.labeled) {
                isSkippableComponent = false
              }
              else {
                cc.skip()
              }
            }

            if (row.components.size == 1) {
              cc.spanX()
            }
          }

          if (isSkippableComponent) {
            if (isSplitRequired) {
              isSplitRequired = false
              cc.split()
            }

            // do not add gap if next component is gear action button
            if (component !== lastComponent && !row.components.get(index + 1).let { it is JLabel && it.icon === AllIcons.General.Gear }) {
              cc.horizontal.gapAfter = gapToBoundSize(UIUtil.DEFAULT_HGAP * 2, true)
            }
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
      val isLabeled = rows.firstOrNull(MigLayoutRow::labeled) != null
      var prevRow: MigLayoutRow? = null
      for (row in rows) {
        columnIndex = 0

        if (isLabeled) {
          columnConstraints.grow(0f, columnIndex++)
        }

        configureComponents(row, prevRow, isLabeled)
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
// see com.intellij.uiDesigner.core.AbstractLayout.DEFAULT_HGAP and DEFAULT_VGAP (multiplied by 2 to achieve the same look (it seems in terms of MigLayout gap is both left and right space))
private fun createLayoutConstraints(gridGapX: Int = UIUtil.DEFAULT_HGAP * 2, gridGapY: Int = UIUtil.DEFAULT_VGAP): LC {
  // no setter for gap, so, create string to parse
  val lc = LC()
  lc.gridGapX = gapToBoundSize(gridGapX, true)
  lc.gridGapY = gapToBoundSize(gridGapY, false)
  lc.insets = ConstraintParser.parseInsets("0px", true)
  return lc
}

internal fun Array<out CCFlags>.create() = if (isEmpty()) null else CC().apply(this)

private fun CC.apply(flags: Array<out CCFlags>): CC {
  for (flag in flags) {
    when (flag) {
      //CCFlags.wrap -> isWrap = true
      CCFlags.grow -> grow()
      CCFlags.growX -> growX()
      CCFlags.growY -> growY()

    // If you have more than one component in a cell the alignment keywords will not work since the behavior would be indeterministic.
    // You can however accomplish the same thing by setting a gap before and/or after the components.
    // That gap may have a minimum size of 0 and a preferred size of a really large value to create a "pushing" gap.
    // There is even a keyword for this: "push". So "gapleft push" will be the same as "align right" and work for multi-component cells as well.
      //CCFlags.right -> horizontal.gapBefore = BoundSize(null, null, null, true, null)

      CCFlags.push -> push()
      CCFlags.pushX -> pushX()
      CCFlags.pushY -> pushY()

      //CCFlags.span -> span()
      //CCFlags.spanX -> spanX()
      //CCFlags.spanY -> spanY()

      //CCFlags.split -> split()

      //CCFlags.skip -> skip()
    }
  }
  return this
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