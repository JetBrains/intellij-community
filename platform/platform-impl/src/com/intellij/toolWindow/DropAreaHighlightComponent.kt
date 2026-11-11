// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.createDropAreaShape
import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * Shows a drop placeholder of a tool window drag. It fills its bounds with [fillColor], or paints nothing for `null`.
 *
 * The corners follow the editor drop placeholder: a corner is rounded only at a corner of [cornerArea].
 */
@ApiStatus.Internal
class DropAreaHighlightComponent(private val fillColor: () -> Color?) : NonOpaquePanel() {
  /**
   * The area in the coordinates of this component that has the rounded corners, or `null` to round all corners.
   */
  var cornerArea: Rectangle? = null
    set(value) {
      if (field != value) {
        field = value
        repaint()
      }
    }

  override fun paint(g: Graphics) {
    g.color = fillColor() ?: return
    val area = Rectangle(size)
    GraphicsUtil.setupAAPainting(g)
    (g as Graphics2D).fill(createDropAreaShape(area, cornerArea ?: area, TabsUtil.getDropAreaArc()))
  }
}