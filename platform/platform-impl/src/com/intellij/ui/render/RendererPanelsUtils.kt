// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.render

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.stripHorizontalInsets
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max

private const val iconTextUnscaledGap: Int = 4

/**
 * RendererPanelsUtils and panels like [IconCompOptionalCompPanel] etc are dedicated to build
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

    /**
     * Gap between icon and related text. Can be extracted into a separate UI constant later if needed
     */
    @JvmStatic
    @Deprecated("Use com.intellij.ui.dsl.listCellRenderer.BuilderKt#listCellRenderer")
    val iconTextGap: Int get() = JBUI.scale(iconTextUnscaledGap)

  }
}

/**
 * Gap between centered and right component. Can be changed by a separate UI constant later if needed
 */
private const val CENTER_RIGHT_GAP = 4

/**
 * Should be used for in cases one label, label with icon, and other similar re
 */
@Deprecated("Use com.intellij.ui.dsl.listCellRenderer.BuilderKt.listCellRenderer instead")
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
        stripHorizontalInsets(value)
        content.add(value, Constraints((content.layout as GridLayout).rootGrid, 2, 0, baselineAlign = true,
                                       gaps = UnscaledGaps(left = CENTER_RIGHT_GAP)))
      }

      field = value
    }

  init {
    stripHorizontalInsets(center)

    createBuilder()
      .cell(iconLabel, baselineAlign = false, gaps = UnscaledGaps(right = iconTextUnscaledGap))
      .cell(center, resizableColumn = true)
  }
}

@Deprecated("Use com.intellij.ui.dsl.listCellRenderer.BuilderKt.listCellRenderer instead")
open class IconPanel : SelectablePanel() {

  /**
   * Content panel allows to trim components that could go outside of selection
   */
  protected val content: JPanel = JPanel(GridLayout())
  protected val iconLabel: JLabel = JLabel()

  init {
    stripHorizontalInsets(iconLabel)
    content.isOpaque = false
    layout = BorderLayout()

    @Suppress("LeakingThis")
    add(content, BorderLayout.CENTER)
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

  /**
   * Calculate width of all non-resizeable components and gaps in the panel
   */
  fun calculateNonResizeableWidth(): Int {
    val contentLayout = content.layout as GridLayout
    val insets = insets
    var result = insets.left + insets.right

    for (component in content.components) {
      val constraint = contentLayout.getConstraints(component as JComponent)!!
      if (constraint.y != 0) {
        throw Exception("Multiple rows are not supported")
      }

      if (constraint.grid !== contentLayout.rootGrid) {
        throw Exception("Sub-grids are not supported")
      }

      result += constraint.gaps.width

      if (constraint.x !in contentLayout.rootGrid.resizableColumns) {
        result += max(component.minimumSize.width, component.preferredSize.width)
      }
    }

    return result
  }

  protected fun createBuilder(): RowsGridBuilder {
    return RowsGridBuilder(content)
      .defaultBaselineAlign(true)
      .resizableRow()
  }
}
