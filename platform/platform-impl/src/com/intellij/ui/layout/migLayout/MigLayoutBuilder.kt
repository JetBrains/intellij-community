// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.layout.migLayout

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.LayoutBuilderImpl
import com.intellij.ui.layout.SpacingConfiguration
import com.intellij.ui.layout.migLayout.patched.MigLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.containers.CollectionFactory
import net.miginfocom.layout.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Container
import javax.swing.*

@ApiStatus.ScheduledForRemoval
@Deprecated("Mig Layout is going to be removed, IDEA-306719")
internal class MigLayoutBuilder(val spacing: SpacingConfiguration) : LayoutBuilderImpl {
  companion object {
    private var hRelatedGap = -1
    private var vRelatedGap = -1

    init {
      JBUIScale.addUserScaleChangeListener {
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
  internal val componentConstraints: MutableMap<Component, CC> = CollectionFactory.createWeakIdentityMap(4, 0.8f)
  override val rootRow: MigLayoutRow = MigLayoutRow(parent = null, builder = this, indent = 0)

  override var preferredFocusedComponent: JComponent? = null
  override var validateCallbacks: MutableList<() -> ValidationInfo?> = mutableListOf()
  override var componentValidateCallbacks: MutableMap<JComponent, () -> ValidationInfo?> = linkedMapOf()
  override var customValidationRequestors: MutableMap<JComponent, MutableList<(() -> Unit) -> Unit>> = linkedMapOf()
  override var applyCallbacks: MutableMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  override var resetCallbacks: MutableMap<JComponent?, MutableList<() -> Unit>> = linkedMapOf()
  override var isModifiedCallbacks: MutableMap<JComponent?, MutableList<() -> Boolean>> = linkedMapOf()

  internal var hideableRowNestingLevel: Int = 0

  internal val defaultComponentConstraintCreator: DefaultComponentConstraintCreator = DefaultComponentConstraintCreator(spacing)

  // keep in mind - MigLayout always creates one more than need column constraints (i.e. for 2 will be 3)
  // it doesn't lead to any issue.
  internal val columnConstraints: AC = AC()

  // MigLayout in any case always creates CC, so, create instance even if it is not required
  private val Component.constraints: CC
    get() = componentConstraints.getOrPut(this) { CC() }

  internal fun updateComponentConstraints(component: Component, callback: CC.() -> Unit) {
    component.constraints.callback()
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

    /**
     * On macOS input fields (text fields, checkboxes, buttons and so on) have focus ring that drawn outside of component border.
     * If reported component dimensions will be equals to visible (when unfocused) component dimensions, focus ring will be clipped.
     *
     * Since LaF cannot control component environment (host component), default safe strategy is to report component dimensions including focus ring.
     * But it leads to an issue - spacing specified for visible component borders, not to compensated. For example, if horizontal space must be 8px,
     * this 8px must be between one visible border of component to another visible border (in the case of macOS Light theme, gray 1px borders).
     * Exactly 8px.
     *
     * So, advanced layout engine, e.g. MigLayout, offers a way to compensate visual padding on the layout container level, not on component level, as a solution.
     */

    lc.isVisualPadding = true
    lc.hideMode = 3

    val rowConstraints = AC()
    (container as JComponent).putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false)
    var isLayoutInsetsAdjusted = false
    container.layout = object : MigLayout(lc, columnConstraints, rowConstraints) {
      override fun layoutContainer(parent: Container) {
        if (!isLayoutInsetsAdjusted) {
          isLayoutInsetsAdjusted = true
          if (container.getClientProperty(DialogWrapper.DIALOG_CONTENT_PANEL_PROPERTY) != null) {
            // since we compensate visual padding, child components should be not clipped, so, we do not use content pane DialogWrapper border (returns null),
            // but instead set insets to our content panel (so, child components are not clipped)
            lc.setInsets(spacing.dialogTopBottom, spacing.dialogLeftRight)
          }
        }

        super.layoutContainer(parent)
      }
    }

    configureGapBetweenColumns(rootRow)

    val physicalRows = collectPhysicalRows(rootRow)

    configureGapsBetweenRows(physicalRows)

    val isNoGrid = layoutConstraints.contains(LCFlags.noGrid)
    if (isNoGrid) {
      physicalRows.flatMap { it.components }.forEach { component ->
        container.add(component, component.constraints)
      }
    }
    else {
      for ((rowIndex, row) in physicalRows.withIndex()) {
        val isLastRow = rowIndex == physicalRows.size - 1
        row.rowConstraints = rowConstraints.index(rowIndex).constaints[rowIndex]
        if (row.noGrid) {
          rowConstraints.noGrid(rowIndex)
        }
        else {
          if (row.gapAfter != null) {
            rowConstraints.gap(row.gapAfter, rowIndex)
          }
          else if (isLastRow) {
            // Do not append default gap to the last row
            rowConstraints.gap("0px!", rowIndex)
          }
        }
        // if constraint specified only for rows 0 and 1, MigLayout will use constraint 1 for any rows with index 1+ (see LayoutUtil.getIndexSafe - use last element if index > size)
        // so, we set for each row to make sure that constraints from previous row will be not applied
        rowConstraints.align("baseline", rowIndex)

        for ((index, component) in row.components.withIndex()) {
          val cc = component.constraints

          // we cannot use columnCount as an indicator of whether to use spanX/wrap or not because component can share cell with another component,
          // in any case MigLayout is smart enough and unnecessary spanX doesn't harm
          if (index == row.components.size - 1) {
            cc.spanX()
            cc.isWrap = true
            if (row.components.size > 1) {
              cc.hideMode = 2   // if hideMode is 3, the wrap constraint won't be processed
            }
          }

          if (index >= row.rightIndex) {
            cc.horizontal.gapBefore = BoundSize(null, null, null, true, null)
          }

          container.add(component, cc)
        }
      }
    }
  }

  private fun collectPhysicalRows(rootRow: MigLayoutRow): List<MigLayoutRow> {
    val result = mutableListOf<MigLayoutRow>()
    fun collect(subRows: List<MigLayoutRow>?) {
      subRows?.forEach { row ->
        // skip synthetic rows that don't have components (e.g. titled row that contains only sub rows)
        if (row.components.isNotEmpty()) {
          result.add(row)
        }
        collect(row.subRows)
      }
    }
    collect(rootRow.subRows)
    return result
  }

  private fun configureGapBetweenColumns(rootRow: MigLayoutRow) {
    var startColumnIndexToApplyHorizontalGap = 0
    if (rootRow.isLabeledIncludingSubRows) {
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

  private fun configureGapsBetweenRows(physicalRows: List<MigLayoutRow>) {
    for (rowIndex in physicalRows.indices) {
      if (rowIndex == 0) continue

      val prevRow = physicalRows[rowIndex - 1]
      val nextRow = physicalRows[rowIndex]

      val prevRowType = getRowType(prevRow)
      val nextRowType = getRowType(nextRow)
      if (prevRowType.isCheckboxRow && nextRowType.isCheckboxRow &&
          (prevRowType == RowType.CHECKBOX_TALL || nextRowType == RowType.CHECKBOX_TALL)) {
        // ugly patching to make UI pretty IDEA-234078
        if (prevRow.gapAfter == null &&
            prevRow.components.all { it.constraints.vertical.gapAfter == null } &&
            nextRow.components.all { it.constraints.vertical.gapBefore == null }) {
          prevRow.gapAfter = "0px!"

          for ((index, component) in prevRow.components.withIndex()) {
            if (index == 0) {
              component.constraints.gapBottom("${spacing.componentVerticalGap}px!")
            }
            else {
              component.constraints.gapBottom("${component.insets.bottom}px!")
            }
          }
          for ((index, component) in nextRow.components.withIndex()) {
            if (index == 0) {
              component.constraints.gapTop("${spacing.componentVerticalGap}px!")
            }
            else {
              component.constraints.gapTop("${component.insets.top}px!")
            }
          }
        }
      }
      else if (prevRowType == RowType.NESTED_PANEL) {
        prevRow.gapAfter = "0px!"
      }
    }
  }

  private fun getRowType(row: MigLayoutRow): RowType {
    if (row.components[0] is JCheckBox) {
      if (row.components.all {
          it is JCheckBox || it is JLabel
        }) return RowType.CHECKBOX
      if (row.components.all {
          it is JCheckBox || it is JLabel ||
          it is JTextField || it is JPasswordField ||
          it is JBTextArea || it is JComboBox<*>
        }) return RowType.CHECKBOX_TALL
    }
    if (row.components.singleOrNull() is DialogPanel) {
      return RowType.NESTED_PANEL
    }
    return RowType.GENERIC
  }

  private enum class RowType {
    GENERIC, CHECKBOX, CHECKBOX_TALL, NESTED_PANEL;

    val isCheckboxRow get() = this == CHECKBOX || this == CHECKBOX_TALL
  }
}

private fun LC.apply(flags: Array<out LCFlags>): LC {
  for (flag in flags) {
    when (flag) {
      LCFlags.noGrid -> isNoGrid = true

      LCFlags.flowY -> isFlowX = false

      LCFlags.fill -> fill()
      LCFlags.fillX -> isFillX = true
      LCFlags.fillY -> isFillY = true

      LCFlags.debug -> debug()
    }
  }
  return this
}