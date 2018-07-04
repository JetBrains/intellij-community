// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.ui.components.noteComponent
import com.intellij.ui.layout.*
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.*
import java.awt.Component
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel

internal class MigLayoutBuilder(val spacing: SpacingConfiguration, val isUseMagic: Boolean = true) : LayoutBuilderImpl {
  companion object {
    private var hRelatedGap = -1
    private var vRelatedGap = -1

    init {
      JBUI.addPropertyChangeListener(JBUI.USER_SCALE_FACTOR_PROPERTY) {
        updatePlatformDefaults()
      }
    }

    private fun updatePlatformDefaults() {
      if (hRelatedGap != -1 && vRelatedGap != -1) {
        PlatformDefaults.setRelatedGap(createUnitValue(hRelatedGap, true), createUnitValue(vRelatedGap, false))
      }
    }

    private fun setRelatedGap(h: Int, v: Int) {
      if (hRelatedGap == h && vRelatedGap == v) {
        return
      }

      hRelatedGap = h
      vRelatedGap = v
      updatePlatformDefaults()
    }
  }

  init {
    setRelatedGap(spacing.horizontalGap, spacing.verticalGap)
  }

  /**
   * Map of component to constraints shared among rows (since components are unique)
   */
  private val componentConstraints: MutableMap<Component, CC> = ContainerUtil.newIdentityTroveMap()
  private val rootRow = MigLayoutRow(parent = null, componentConstraints = componentConstraints, builder = this, indent = 0)

  val defaultComponentConstraintCreator = DefaultComponentConstraintCreator(spacing)

  // keep in mind - MigLayout always creates one more than need column constraints (i.e. for 2 will be 3)
  // it doesn't lead to any issue.
  val columnConstraints = AC()

  override fun newRow(label: JLabel?, buttonGroup: ButtonGroup?, separated: Boolean): Row {
    return rootRow.createChildRow(label = label, buttonGroup = buttonGroup, separated = separated)
  }

  override fun noteRow(text: String, linkHandler: ((url: String) -> Unit)?) {
    val cc = CC()
    cc.vertical.gapBefore = gapToBoundSize(if (rootRow.subRows == null) spacing.verticalGap else spacing.largeVerticalGap, false)
    cc.vertical.gapAfter = gapToBoundSize(spacing.verticalGap * 2, false)

    val row = rootRow.createChildRow(label = null, noGrid = true)
    row.apply {
      val noteComponent = noteComponent(text, linkHandler)
      componentConstraints.put(noteComponent, cc)
      noteComponent()
    }
  }

  override fun build(container: Container, layoutConstraints: Array<out LCFlags>) {
    val lc = createLayoutConstraints()
    lc.gridGapY = gapToBoundSize(spacing.verticalGap, false)
    if (layoutConstraints.isEmpty()) {
      lc.fillX()
      // not fillY because it leads to enormously large cells - we use cc `push` in addition to cc `grow` as a more robust and easy solution
    }
    else {
      lc.apply(layoutConstraints)
    }

    lc.isVisualPadding = spacing.isCompensateVisualPaddings
    lc.hideMode = 3

    // if constraint specified only for rows 0 and 1, MigLayout will use constraint 1 for any rows with index 1+ (see LayoutUtil.getIndexSafe - use last element if index > size)
    val rowConstraints = AC()
    rowConstraints.align(if (isUseMagic) "baseline" else "top")

    (container as JComponent).putClientProperty("isVisualPaddingCompensatedOnComponentLevel", false)
    var isLayoutInsetsAdjusted = false
    container.layout = object : MigLayout(lc, columnConstraints, rowConstraints) {
      override fun layoutContainer(parent: Container) {
        if (!isLayoutInsetsAdjusted) {
          isLayoutInsetsAdjusted = true

          var topParent = parent.parent
          while (topParent != null) {
            if (topParent is JDialog) {
              val topBottom = createUnitValue(spacing.dialogTopBottom, false)
              val leftRight = createUnitValue(spacing.dialogLeftRight, true)
              // since we compensate visual padding, child components should be not clipped, so, we do not use content pane DialogWrapper border (returns null),
              // but instead set insets to our content panel (so, child components are not clipped)
              lc.insets = arrayOf(topBottom, leftRight, topBottom, leftRight)
              break
            }
            topParent = topParent.parent
          }
        }

        super.layoutContainer(parent)
      }
    }

    val isNoGrid = layoutConstraints.contains(LCFlags.noGrid)

    var rowIndex = 0
    fun configureComponents(row: MigLayoutRow) {
      val lastComponent = row.components.lastOrNull()
      for ((index, component) in row.components.withIndex()) {
        // MigLayout in any case always creates CC, so, create instance even if it is not required
        val cc = componentConstraints.get(component) ?: CC()

        if (isNoGrid) {
          container.add(component, cc)
          continue
        }

        // we cannot use columnCount as an indicator of whether to use spanX/wrap or not because component can share cell with another component,
        // in any case MigLayout is smart enough and unnecessary spanX/wrap doesn't harm
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
      for (row in rows) {
        configureComponents(row)
        row.subRows?.let {
          processRows(it)
        }
      }
    }

    rootRow.subRows?.let {
      configureGapBetweenColumns(it)
      processRows(it)
    }

    // do not hold components
    componentConstraints.clear()
  }

  private fun configureGapBetweenColumns(subRows: List<MigLayoutRow>) {
    var startColumnIndexToApplyHorizontalGap = 0
    if (subRows.any { it.isLabeledIncludingSubRows }) {
      // using columnConstraints instead of component gap allows easy debug (proper painting of debug grid)
      columnConstraints.gap("${spacing.labelColumnHorizontalGap}px!", 0)
      columnConstraints.grow(0f, 0)
      startColumnIndexToApplyHorizontalGap = 1
    }

    val gapAfter = "${spacing.horizontalGap}px!"
    for (i in startColumnIndexToApplyHorizontalGap until rootRow.columnIndexIncludingSubRows) {
      columnConstraints.gap(gapAfter, i)
    }
  }
}

internal fun gapToBoundSize(value: Int, isHorizontal: Boolean): BoundSize {
  val unitValue = createUnitValue(value, isHorizontal)
  return BoundSize(unitValue, unitValue, null, false, null)
}

fun createLayoutConstraints(): LC {
  val lc = LC()
  lc.gridGapX = gapToBoundSize(0, true)
  lc.insets("0px")
  return lc
}

private fun createUnitValue(value: Int, isHorizontal: Boolean): UnitValue {
  return UnitValue(value.toFloat(), "px", isHorizontal, UnitValue.STATIC, null)
}

private fun LC.apply(flags: Array<out LCFlags>): LC {
  for (flag in flags) {
    @Suppress("NON_EXHAUSTIVE_WHEN")
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