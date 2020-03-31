// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * FlowLayout subclass that fully supports wrapping of components.
 */
class WrapLayout : FlowLayout {

  /**
   * Constructs a new `WrapLayout` with a left
   * alignment and a default 5-unit horizontal and vertical gap.
   */
  constructor() : super()

  /**
   * Constructs a new `FlowLayout` with the specified
   * alignment and a default 5-unit horizontal and vertical gap.
   * The value of the alignment argument must be one of
   * `WrapLayout`, `WrapLayout`,
   * or `WrapLayout`.
   * @param align the alignment value
   */
  constructor(align: Int) : super(align)

  /**
   * Creates a new flow layout manager with the indicated alignment
   * and the indicated horizontal and vertical gaps.
   *
   *
   * The value of the alignment argument must be one of
   * `WrapLayout`, `WrapLayout`,
   * or `WrapLayout`.
   * @param align the alignment value
   * @param hgap the horizontal gap between components
   * @param vgap the vertical gap between components
   */
  constructor(align: Int, hgap: Int, vgap: Int) : super(align, hgap, vgap)

  /**
   * Returns the preferred dimensions for this layout given the
   * *visible* components in the specified target container.
   * @param target the component which needs to be laid out
   * @return the preferred dimensions to lay out the
   * subcomponents of the specified container
   */
  override fun preferredLayoutSize(target: Container): Dimension {
    return layoutSize(target, true)
  }

  /**
   * Returns the minimum dimensions needed to layout the *visible*
   * components contained in the specified target container.
   * @param target the component which needs to be laid out
   * @return the minimum dimensions to lay out the
   * subcomponents of the specified container
   */
  override fun minimumLayoutSize(target: Container): Dimension {
    val minimum = layoutSize(target, false)
    minimum.width -= hgap + 1
    return minimum
  }

  /**
   * Returns the minimum or preferred dimension needed to layout the target
   * container.
   *
   * @param target target to get layout size for
   * @param preferred should preferred size be calculated
   * @return the dimension to layout the target container
   */
  private fun layoutSize(target: Container, preferred: Boolean): Dimension {
    synchronized(target.treeLock) {
      //  Each row must fit with the width allocated to the containter.
      //  When the container width = 0, the preferred width of the container
      //  has not yet been calculated so lets ask for the maximum.
      var container = target
      while (container.size.width == 0 && container.parent != null) {
        container = container.parent
      }

      var targetWidth = container.size.width
      if (targetWidth == 0) {
        targetWidth = Integer.MAX_VALUE
      }

      val hgap = hgap
      val vgap = vgap
      val insets = target.insets
      val horizontalInsetsAndGap = insets.left + insets.right + hgap * 2
      val maxWidth = targetWidth - horizontalInsetsAndGap

      //  Fit components into the allowed width
      val dim = Dimension(0, 0)
      var rowWidth = 0
      var rowHeight = 0

      val nmembers = target.componentCount

      for (i in 0 until nmembers) {
        val m = target.getComponent(i)

        if (m.isVisible) {
          val d = if (preferred) m.preferredSize else m.minimumSize

          //  Can't add the component to current row. Start a new row.
          if (rowWidth + hgap + d.width > maxWidth) {
            addRow(dim, rowWidth, rowHeight)
            rowWidth = 0
            rowHeight = 0
          }

          //  Add a horizontal gap for all components after the first
          if (rowWidth != 0) {
            rowWidth += hgap
          }

          rowWidth += d.width
          rowHeight = Math.max(rowHeight, d.height)
        }
      }

      addRow(dim, rowWidth, rowHeight)

      dim.width += horizontalInsetsAndGap
      dim.height += insets.top + insets.bottom + vgap * 2

      //	When using a scroll pane or the DecoratedLookAndFeel we need to
      //  make sure the preferred size is less than the size of the
      //  target containter so shrinking the container size works
      //  correctly. Removing the horizontal gap is an easy way to do this.
      val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
      if (scrollPane != null && target.isValid) {
        dim.width -= hgap + 1
      }

      return dim
    }
  }

  /**
   *  A new row has been completed. Use the dimensions of this row
   *  to update the preferred size for the container.
   *
   *  @param dim update the width and height when appropriate
   *  @param rowWidth the width of the row to add
   *  @param rowHeight the height of the row to add
   */
  private fun addRow(dim: Dimension, rowWidth: Int, rowHeight: Int) {
    dim.width = Math.max(dim.width, rowWidth)

    if (dim.height > 0) {
      dim.height += vgap
    }

    dim.height += rowHeight
  }
}