// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.components.JBComponent
import java.awt.*
import javax.swing.JPanel
import javax.swing.border.Border

/**
 * @author Konstantin Bulenkov
 */
open class JBPanel<T : JBPanel<T>>(
  layout: LayoutManager? = null,
  isDoubleBuffered: Boolean = true
) : JPanel(layout, isDoubleBuffered), JBComponent<T> {

  constructor(layout: LayoutManager) : this(layout, true)
  constructor(isDoubleBuffered: Boolean) : this(null, isDoubleBuffered)
  constructor() : this(null, true)

  private var myPreferredWidth: Int? = null
  private var myPreferredHeight: Int? = null
  private var myMaximumWidth: Int? = null
  private var myMaximumHeight: Int? = null
  private var myMinimumWidth: Int? = null
  private var myMinimumHeight: Int? = null



  @Suppress("UNCHECKED_CAST")
  private fun self(): T = this as T

  override fun getComponentGraphics(graphics: Graphics): Graphics {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
  }

  override fun withBorder(border: Border): T {
    setBorder(border)
    return self()
  }

  override fun withFont(font: JBFont): T {
    setFont(font)
    return self()
  }

  override fun andTransparent(): T {
    setOpaque(false)
    return self()
  }

  override fun andOpaque(): T {
    setOpaque(true)
    return self()
  }

  fun withBackground(background: Color?): T {
    setBackground(background)
    return self()
  }

  fun withPreferredWidth(width: Int): T {
    myPreferredWidth = width
    return self()
  }

  fun withPreferredHeight(height: Int): T {
    myPreferredHeight = height
    return self()
  }

  fun withPreferredSize(width: Int, height: Int): T {
    myPreferredWidth = width
    myPreferredHeight = height
    return self()
  }

  fun withMaximumWidth(width: Int): T {
    myMaximumWidth = width
    return self()
  }

  fun withMaximumHeight(height: Int): T {
    myMaximumHeight = height
    return self()
  }

  fun withMaximumSize(width: Int, height: Int): T {
    myMaximumWidth = width
    myMaximumHeight = height
    return self()
  }

  fun withMinimumWidth(width: Int): T {
    myMinimumWidth = width
    return self()
  }

  fun withMinimumHeight(height: Int): T {
    myMinimumHeight = height
    return self()
  }

  override fun getPreferredSize(): Dimension {
    return getSize(super.getPreferredSize(), myPreferredWidth, myPreferredHeight, isPreferredSizeSet)
  }

  override fun getMaximumSize(): Dimension {
    return getSize(super.getMaximumSize(), myMaximumWidth, myMaximumHeight, isMaximumSizeSet)
  }

  override fun getMinimumSize(): Dimension {
    return getSize(super.getMinimumSize(), myMinimumWidth, myMinimumHeight, isMinimumSizeSet)
  }

  override fun setPreferredSize(preferredSize: Dimension?) {
    super.setPreferredSize(preferredSize)
  }


  companion object {
    private fun getSize(size: Dimension, width: Int?, height: Int?, isSet: Boolean): Dimension {
      if (!isSet) {
        if (width != null) size.width = JBUIScale.scale(width)
        if (height != null) size.height = JBUIScale.scale(height)
      }
      return size
    }
  }
}
