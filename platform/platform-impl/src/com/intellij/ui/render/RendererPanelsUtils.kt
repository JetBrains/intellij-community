// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.render

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.math.max

/**
 * RendererPanelsUtils and panels like [IconCompCompPanel], [IconCompOptionalCompPanel] etc are dedicated to build
 * standard simple renderers, which should cover most single-lined cases. Below are the reasons why use renderers from this file:
 *
 * 1. There are many similar renderers based on different layouts. It's hard to support them all and there are many duplicated code
 * 2. Renderers use different gaps between components. For example icon in the start of a row can be a part of [JLabel],
 * which uses by default non-scaled icon gap. So icon alignment can be broken with different renderers in one list
 * 3. Sometimes simple renderers have several nested sub-panels. That's unnecessary complication for standard renderers and hard to debug and support
 * 4. New UI requires rounded selection, which is implemented in [SelectablePanel]. All renderers in this file implement [SelectablePanel],
 * so there is no need to wrap renderers into it later
 *
 * Additional ideas how to keep renderers simple and supportable:
 * 1. Don't set background for renderer and children separately: just set background for top panel and use opaque = false for children
 * 2. Avoid [UIUtil.setBackgroundRecursively], [UIUtil.setOpaqueRecursively] and similar. Renderers can be wrapped into other components,
 * and it's hard to control the result
 * 3. Don't set icons by calling [JLabel.setIcon], [SimpleColoredComponent.setIcon] and similar, they use different icon gaps. Use
 * panels from this file with a dedicated place for icon
 * 4. Don't use children insets to control insets around renderer (for example left inset for first component in a row or top/bottom insets),
 * use border for top panel instead. Therefore, such renderers can be configured with custom insets when needed
 * 5. For very specific/complex renderers keep writing them separately, don't complicate simple renderers
 */
class RendererPanelsUtils {

  companion object {
    internal const val iconTextUnscaledGap = 4

    /**
     * Gap between icon and related text. Can be extracted into a separate UI constant later if needed
     */
    @JvmStatic
    val iconTextGap: Int get() = JBUI.scale(iconTextUnscaledGap)

    /**
     * Calculate width of all non-resizeable components and gaps in the panel. Works only if layout is
     * [GridLayout] and there is only one row. Throws exception otherwise
     */
    @JvmStatic
    fun calculateNonResizeableWidth(panel: JPanel): Int {
      val layout = panel.layout as GridLayout
      val insets = panel.insets
      var result = insets.left + insets.right

      for (component in panel.components) {
        val constraint = layout.getConstraints(component as JComponent)!!
        if (constraint.y != 0) {
          throw Exception("Multiple rows are not supported")
        }

        if (constraint.grid !== layout.rootGrid) {
          throw Exception("Sub-grids are not supported")
        }

        result += constraint.gaps.width

        if (constraint.x !in layout.rootGrid.resizableColumns) {
          result += max(component.minimumSize.width, component.preferredSize.width)
        }
      }

      return result
    }
  }
}

/**
 * Gap between centered and right component. Can be changed by a separate UI constant later if needed
 */
private const val centerRightGap = 4

open class IconCompCompPanel<C1 : JComponent, C2 : JComponent>(val center: C1, val right: C2) : IconPanel() {

  init {
    resetHorizontalInsets(center, right)

    createBuilder(this)
      .cell(iconLabel, baselineAlign = false, gaps = UnscaledGaps(right = RendererPanelsUtils.iconTextUnscaledGap))
      .cell(center, resizableColumn = true)
      .cell(right, gaps = UnscaledGaps(left = centerRightGap))
  }
}

/**
 * Should be used for in cases one label, label with icon, and other similar re
 */
open class IconCompOptionalCompPanel<C1 : JComponent>(
  val center: C1) : IconPanel() {

  var right: JComponent? = null
    set(value) {
      if (value === field) {
        return
      }

      if (field != null) {
        remove(field)
      }

      if (value != null) {
        resetHorizontalInsets(value)
        add(value, Constraints((layout as GridLayout).rootGrid, 2, 0, baselineAlign = true, gaps = UnscaledGaps(left = centerRightGap)))
      }

      field = value
    }

  init {
    resetHorizontalInsets(center)

    createBuilder(this)
      .cell(iconLabel, baselineAlign = false, gaps = UnscaledGaps(right = RendererPanelsUtils.iconTextUnscaledGap))
      .cell(center, resizableColumn = true)
  }
}

open class IconPanel : SelectablePanel() {

  protected val iconLabel = JLabel()

  init {
    resetHorizontalInsets(iconLabel)
  }

  open fun reset() {
    selectionArc = 0
    selectionArcCorners = SelectionArcCorners.ALL
    selectionColor = null
    selectionInsets = JBUI.emptyInsets()
    iconLabel.isVisible = false
  }

  fun setIcon(icon: Icon?) {
    iconLabel.isVisible = icon != null
    iconLabel.icon = icon
  }
}

/**
 * Resets any horizontal insets ([EmptyBorder], [JBEmptyBorder]) and ipads ([SimpleColoredComponent.getIpad])
 */
fun resetHorizontalInsets(vararg components: JComponent) {
  for (component in components) {
    component.putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)

    val border = component.border
    if (border != null && (border.javaClass === EmptyBorder::class.java || border.javaClass === JBEmptyBorder::class.java)) {
      val insets = (border as EmptyBorder).borderInsets
      component.border = EmptyBorder(insets.top, 0, insets.bottom, 0)
    }

    if (component is SimpleColoredComponent) {
      component.ipad = Insets(component.ipad.top, 0, component.ipad.bottom, 0)
    }
  }
}

private fun createBuilder(panel: JPanel): RowsGridBuilder {
  panel.layout = GridLayout()
  val result = RowsGridBuilder(panel)
  result
    .defaultBaselineAlign(true)
    .resizableRow()
  return result
}
