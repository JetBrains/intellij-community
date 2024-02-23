/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.intellij.util.ui.html

import java.awt.Toolkit
import javax.swing.text.Element
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import javax.swing.text.html.CSS

/**
 * Supports line-height property
 */
internal class LineViewEx(elem: Element) : ParagraphViewEx(elem) {

  /** Last place painted at.  */
  private var wrap = false

  /**
   * Preformatted lines are not suppressed if they
   * have only whitespace, so they are always visible.
   */
  override fun isVisible(): Boolean {
    return true
  }

  /**
   * Determines the minimum span for this view along an
   * axis.  The preformatted line should refuse to be
   * sized less than the preferred size.
   *
   * @param axis may be either `View.X_AXIS` or
   * `View.Y_AXIS`
   * @return  the minimum span the view can be rendered into
   * @see View.getPreferredSpan
   */
  override fun getMinimumSpan(axis: Int): Float {
    return if (wrap) super.getMinimumSpan(axis) else getPreferredSpan(axis)
  }

  /**
   * Gets the resize weight for the specified axis.
   *
   * @param axis may be either X_AXIS or Y_AXIS
   * @return the weight
   */
  override fun getResizeWeight(axis: Int): Int {
    return when (axis) {
      X_AXIS -> 1
      Y_AXIS -> 0
      else -> throw IllegalArgumentException("Invalid axis: $axis")
    }
  }

  /**
   * Gets the alignment for an axis.
   *
   * @param axis may be either X_AXIS or Y_AXIS
   * @return the alignment
   */
  override fun getAlignment(axis: Int): Float {
    if (axis == X_AXIS) {
      return 0f
    }
    return super.getAlignment(axis)
  }

  /**
   * Lays out the children.  If the layout span has changed,
   * the rows are rebuilt.  The superclass functionality
   * is called after checking and possibly rebuilding the
   * rows.  If the height has changed, the
   * `preferenceChanged` method is called
   * on the parent since the vertical preference is
   * rigid.
   *
   * @param width  the width to lay out against >= 0.  This is
   * the width inside of the inset area.
   * @param height the height to lay out against >= 0 (not used
   * by paragraph, but used by the superclass).  This
   * is the height inside of the inset area.
   */
  override fun layout(width: Int, height: Int) {
    super.layout(if (wrap) width else Int.MAX_VALUE - 1, height)
  }

  /**
   * Returns the next tab stop position given a reference position.
   * This view implements the tab coordinate system, and calls
   * `getTabbedSpan` on the logical children in the process
   * of layout to determine the desired span of the children.  The
   * logical children can delegate their tab expansion upward to
   * the paragraph which knows how to expand tabs.
   * `LabelView` is an example of a view that delegates
   * its tab expansion needs upward to the paragraph.
   *
   *
   * This is implemented to try and locate a `TabSet`
   * in the paragraph element's attribute set.  If one can be
   * found, its settings will be used, otherwise a default expansion
   * will be provided.  The base location for tab expansion
   * is the left inset from the paragraphs most recent allocation
   * (which is what the layout of the children is based upon).
   *
   * @param x the X reference position
   * @param tabOffset the position within the text stream
   * that the tab occurred at >= 0.
   * @return the trailing end of the tab expansion >= 0
   */
  override fun nextTabStop(x: Float, tabOffset: Int): Float {
    // If the text isn't left justified, offset by 10 pixels!
    if (tabSet == null &&
        StyleConstants.getAlignment(attributes) ==
        StyleConstants.ALIGN_LEFT
    ) {
      return getPreTab(x, tabOffset)
    }
    return super.nextTabStop(x, tabOffset)
  }

  /**
   * Returns the location for the tab.
   */
  private fun getPreTab(x: Float, tabOffset: Int): Float {
    val d = document
    val v = getViewAtPosition(tabOffset, null)
    if ((d is StyledDocument) && v != null) {
      // Assume f is fixed point.
      val f = d.getFont(v.attributes)
      val c = container
      val fm = if ((c != null)) c.getFontMetrics(f) else Toolkit.getDefaultToolkit().getFontMetrics(f)
      val width = CHARS_PER_TAB * fm.charWidth('W')
      val tb = tabBase.toInt()
      return (((x.toInt() - tb) / width + 1) * width + tb).toFloat()
    }
    return 10.0f + x
  }


  override fun setPropertiesFromAttributes() {
    super.setPropertiesFromAttributes()
    val parent = parent
    wrap = parent != null && parent.attributes.containsAttribute(CSS.Attribute.WHITE_SPACE, "pre-wrap")
  }

  companion object {
    private const val CHARS_PER_TAB: Int = 8
  }
}
