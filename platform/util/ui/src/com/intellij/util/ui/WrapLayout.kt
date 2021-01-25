// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.math.*

/**
 * FlowLayout subclass that fully supports wrapping of components.
 */
open class WrapLayout : FlowLayout {

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

  var fillWidth: Boolean = false

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
          val d =  if (preferred) preferredSize(m) else m.minimumSize

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
          rowHeight = max(rowHeight, d.height)
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

      dim.width = min(dim.width, maxWidth)
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
    dim.width = max(dim.width, rowWidth)

    if (dim.height > 0) {
      dim.height += vgap
    }

    dim.height += rowHeight
  }

  override fun layoutContainer(target: Container) {
    synchronized(target.treeLock) {
      val insets = target.insets
      val maxwidth = target.width - (insets.left + insets.right + hgap * 2)
      val nmembers = target.componentCount
      var x = 0
      var y = insets.top + vgap
      var rowh = 0
      var start = 0
      val ltr = target.componentOrientation.isLeftToRight
      val useBaseline = alignOnBaseline
      val ascent = IntArray(nmembers)
      val descent = IntArray(nmembers)
      for (i in 0 until nmembers) {
        val m = target.getComponent(i)
        if (m.isVisible) {
          val d = preferredSize(m)
          m.setSize(d.width, d.height)
          if (useBaseline) {
            val baseline = m.getBaseline(d.width, d.height)
            if (baseline >= 0) {
              ascent[i] = baseline
              descent[i] = d.height - baseline
            }
            else {
              ascent[i] = -1
            }
          }
          if (x == 0 || x + d.width <= maxwidth) {
            if (x > 0) {
              x += hgap
            }
            x += d.width
            rowh = max(rowh, d.height)
          }
          else {
            rowh = moveComponents(target, insets.left + hgap, y,
                                  maxwidth - x, rowh, start, i, ltr,
                                  useBaseline, ascent, descent)
            x = d.width
            y += vgap + rowh
            rowh = d.height
            start = i
          }
        }
      }
      moveComponents(target, insets.left + hgap, y, maxwidth - x, rowh,
                     start, nmembers, ltr, useBaseline, ascent, descent)
    }
  }

  private fun preferredSize(m: Component) =
    Dimension(max(m.preferredSize.width, m.minimumSize.width), max(m.preferredSize.height, m.minimumSize.height))

  private fun moveComponents(target: Container, _x: Int, y: Int, width: Int, _height: Int,
                                  rowStart: Int, rowEnd: Int, ltr: Boolean,
                                  useBaseline: Boolean, ascent: IntArray,
                                  descent: IntArray): Int {
    var x = _x
    var height = _height
    when (alignment) {
      LEFT -> x += if (ltr) 0 else width
      CENTER -> x += width / 2
      RIGHT -> x += if (ltr) width else 0
      LEADING -> {
      }
      TRAILING -> x += width
    }
    var maxAscent = 0
    var nonBaselineHeight = 0
    var baselineOffset = 0
    if (useBaseline) {
      var maxDescent = 0
      for (i in rowStart until rowEnd) {
        val m = target.getComponent(i)
        if (m.isVisible) {
          if (ascent[i] >= 0) {
            maxAscent = max(maxAscent, ascent[i])
            maxDescent = max(maxDescent, descent[i])
          }
          else {
            nonBaselineHeight = max(m.height, nonBaselineHeight)
          }
        }
      }
      height = max(maxAscent + maxDescent, nonBaselineHeight)
      baselineOffset = (height - maxAscent - maxDescent) / 2
    }

    var expand = 1.0
    if (fillWidth) {
      var sum = 0.0
      for (i in rowStart until rowEnd) {
        val m = target.getComponent(i)
        if (m.isVisible) {
          sum += max(m.preferredSize.width, m.minimumSize.width)
        }
      }
      expand = target.width.toDouble() / sum
    }

    for (i in rowStart until rowEnd) {
      val m = target.getComponent(i)
      if (m.isVisible) {
        val cy: Int = if (useBaseline && ascent[i] >= 0) {
          y + baselineOffset + maxAscent - ascent[i]
        }
        else {
          y + (height - m.height) / 2
        }
        val w = if (fillWidth) floor(max(m.preferredSize.width, m.minimumSize.width) * expand).toInt() else m.width
        if (ltr) {
          m.setBounds(x, cy, w, m.height)
        }
        else {
          m.setBounds(target.width - x - w, cy, w, m.height)
        }
        x += m.width + hgap
      }
    }
    return height
  }
}