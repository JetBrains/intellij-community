// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions.toolbar

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

internal class XNextToolWindowButtonLook : HeaderToolbarButtonLook() {
    override fun paintBorder(g: Graphics, component: JComponent?, state: Int) {
      val g = IdeBackgroundUtil.getOriginalGraphics(g)
      super.paintBorder(g, component, state)
      component ?: return
      if(state == ActionButtonComponent.PUSHED) {
        val g2 = g.create() as Graphics2D
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

          val arc = buttonArc.float
          val rect = Rectangle(component.size)
          JBInsets.removeFrom(rect, component.getInsets())

          val shape = Area(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), arc, arc))
          rect.height -= JBUI.scale(2)
          shape.subtract(Area(rect))

          g2.color = JBUI.CurrentTheme.DefaultTabs.underlineColor()
          g2.fill(shape)
        } finally {
          g2.dispose()
        }
      }

    }
  }