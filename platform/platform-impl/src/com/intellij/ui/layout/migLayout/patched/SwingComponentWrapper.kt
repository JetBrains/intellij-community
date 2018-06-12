/*
 * License (BSD):
 * ==============
 *
 * Copyright (c) 2004, Mikael Grev, MiG InfoCom AB. (miglayout (at) miginfocom (dot) com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * Neither the name of the MiG InfoCom AB nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 * @version 1.0
 * @author Mikael Grev, MiG InfoCom AB
 *         Date: 2006-sep-08
 */
package com.intellij.ui.layout.migLayout.patched

import com.intellij.ide.ui.laf.VisualPaddingsProvider
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.util.ThreeState
import net.miginfocom.layout.ComponentWrapper
import net.miginfocom.layout.ContainerWrapper
import net.miginfocom.layout.LayoutUtil
import net.miginfocom.layout.PlatformDefaults
import java.awt.*
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.border.LineBorder

/** Debug color for component bounds outline.
 */
private val DB_COMP_OUTLINE = Color(0, 0, 200)

internal open class SwingComponentWrapper(private val c: JComponent) : ComponentWrapper {
  private var hasBaseLine = ThreeState.UNSURE
  private var isPrefCalled = false

  private var visualPaddings: IntArray? = null

  override fun getBaseline(width: Int, height: Int): Int {
    var h = height
    val visualPaddings = visualPadding
    if (h < 0) {
      h = c.height
    }
    else if (visualPaddings != null) {
      h = height + visualPaddings[0] + visualPaddings[2]
    }
    var baseLine = c.getBaseline(if (width < 0) c.width else width, h)
    if (baseLine != -1 && visualPaddings != null) {
      baseLine -= visualPaddings[0]
    }
    return baseLine
  }

  override fun getComponent() = c

  override fun getPixelUnitFactor(isHor: Boolean): Float {
    throw RuntimeException("Do not use LPX/LPY")
  }

  override fun getX() = c.x

  override fun getY() = c.y

  override fun getHeight() = c.height

  override fun getWidth() = c.width

  override fun getScreenLocationX(): Int {
    val p = Point()
    SwingUtilities.convertPointToScreen(p, c)
    return p.x
  }

  override fun getScreenLocationY(): Int {
    val p = Point()
    SwingUtilities.convertPointToScreen(p, c)
    return p.y
  }

  override fun getMinimumHeight(sz: Int): Int {
    if (!isPrefCalled) {
      c.preferredSize // To defeat a bug where the minimum size is different before and after the first call to getPreferredSize();
      isPrefCalled = true
    }
    return c.minimumSize.height
  }

  override fun getMinimumWidth(sz: Int): Int {
    if (!isPrefCalled) {
      c.preferredSize // To defeat a bug where the minimum size is different before and after the first call to getPreferredSize();
      isPrefCalled = true
    }
    return c.minimumSize.width
  }

  override fun getPreferredHeight(sz: Int): Int {
    // If the component has not gotten size yet and there is a size hint, trick Swing to return a better height.
    if (c.width == 0 && c.height == 0 && sz != -1) {
      c.setBounds(c.x, c.y, sz, 1)
    }
    return c.preferredSize.height
  }

  override fun getPreferredWidth(sz: Int): Int {
    // If the component has not gotten size yet and there is a size hint, trick Swing to return a better height.
    if (c.width == 0 && c.height == 0 && sz != -1) {
      c.setBounds(c.x, c.y, 1, sz)
    }
    return c.preferredSize.width
  }

  override fun getMaximumHeight(sz: Int) = if (c.isMaximumSizeSet) c.maximumSize.height else Integer.MAX_VALUE

  override fun getMaximumWidth(sz: Int) = if (c.isMaximumSizeSet) c.maximumSize.width else Integer.MAX_VALUE

  override fun getParent(): ContainerWrapper? {
    val p = c.parent ?: return null
    return SwingContainerWrapper(p as JComponent)
  }

  override fun getHorizontalScreenDPI(): Int {
    try {
      return c.toolkit.screenResolution
    }
    catch (ex: HeadlessException) {
      return PlatformDefaults.getDefaultDPI()
    }
  }

  override fun getVerticalScreenDPI(): Int {
    try {
      return c.toolkit.screenResolution
    }
    catch (ex: HeadlessException) {
      return PlatformDefaults.getDefaultDPI()
    }
  }

  override fun getScreenWidth(): Int {
    try {
      return c.toolkit.screenSize.width
    }
    catch (ignore: HeadlessException) {
      return 1024
    }

  }

  override fun getScreenHeight(): Int {
    try {
      return c.toolkit.screenSize.height
    }
    catch (ignore: HeadlessException) {
      return 768
    }
  }

  override fun hasBaseline(): Boolean {
    if (hasBaseLine == ThreeState.UNSURE) {
      try {
        val d = c.minimumSize
        hasBaseLine = ThreeState.fromBoolean(getBaseline(d.width, d.height) > -1)
      }
      catch (ignore: Throwable) {
        hasBaseLine = ThreeState.NO
      }
    }
    return hasBaseLine.toBoolean()
  }

  override fun getLinkId(): String? = c.name

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    c.setBounds(x, y, width, height)
  }

  override fun isVisible() = c.isVisible

  override fun getVisualPadding(): IntArray? {
    visualPaddings?.let {
      return it
    }

    val component = when (c) {
      is ComponentWithBrowseButton<*> -> c.childComponent
      else -> c
    }

    val border = component.border ?: return null
    if (border is LineBorder) {
      return null
    }

    val paddings = when (border) {
                     is VisualPaddingsProvider -> border.getVisualPaddings(c)
                     else -> border.getBorderInsets(component)
                   } ?: return null

    if (paddings.top == 0 && paddings.left == 0 && paddings.bottom == 0 && paddings.right == 0) {
      return null
    }

    visualPaddings = intArrayOf(paddings.top, paddings.left, paddings.bottom, paddings.right)
    return visualPaddings
  }

  override fun paintDebugOutline(showVisualPadding: Boolean) {
    if (!c.isShowing) {
      return
    }

    val g = c.graphics as? Graphics2D ?: return

    g.paint = DB_COMP_OUTLINE
    g.stroke = BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, floatArrayOf(2f, 4f), 0f)
    g.drawRect(0, 0, width - 1, height - 1)

    if (showVisualPadding) {
      val padding = visualPadding
      if (padding != null) {
        g.color = Color.GREEN
        g.drawRect(padding[1], padding[0], width - 1 - (padding[1] + padding[3]), height - 1 - (padding[0] + padding[2]))
      }
    }
  }

  override fun getComponentType(disregardScrollPane: Boolean): Int {
    throw RuntimeException("Should be not called and used")
  }

  override fun getLayoutHashCode(): Int {
    var d = c.maximumSize
    var hash = d.width + (d.height shl 5)

    d = c.preferredSize
    hash += (d.width shl 10) + (d.height shl 15)

    d = c.minimumSize
    hash += (d.width shl 20) + (d.height shl 25)

    if (c.isVisible)
      hash += 1324511

    linkId?.let {
      hash += it.hashCode()
    }

    return hash
  }

  override fun hashCode() = component.hashCode()

  override fun equals(other: Any?) = other is ComponentWrapper && c == other.component

  override fun getContentBias(): Int {
    return when {
      c is JTextArea || c is JEditorPane || java.lang.Boolean.TRUE == c.getClientProperty("migLayout.dynamicAspectRatio") -> LayoutUtil.HORIZONTAL
      else -> -1
    }
  }
}
