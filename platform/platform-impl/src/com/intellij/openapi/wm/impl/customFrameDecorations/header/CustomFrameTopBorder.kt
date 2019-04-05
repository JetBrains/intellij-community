// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.border.Border

/*
class CustomFrameTopBorder : Border{
    companion object {
        val THICKNESS = 1
        val MENUBAR_BORDER_COLOR: Color = JBColor.namedColor("MenuBar.borderColor", JBColor(Gray.xCD, Gray.x51))
        val ACTIVE_COLOR = ObjectUtils.notNull(Toolkit.getDefaultToolkit().getDesktopProperty("win.dwm.colorizationColor") as Color
        ) { Toolkit.getDefaultToolkit().getDesktopProperty("win.frame.activeBorderColor") as Color }
        val INACTIVE_COLOR = Toolkit.getDefaultToolkit().getDesktopProperty("win.3d.shadowColor") as Color

    }

    private var state = Frame.NORMAL

    fun setState(value: Int) {
        state = value
    }

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {

        if (topNeeded()) {
            g.color = if (myIsActive) ACTIVE_COLOR else INACTIVE_COLOR
            LinePainter2D.paint(g as Graphics2D, x.toDouble(), y.toDouble(), width.toDouble(), y.toDouble())
        }

        g.color = MENUBAR_BORDER_COLOR
        val y1 = y + height - JBUI.scale(THICKNESS)
        LinePainter2D.paint(g as Graphics2D, x.toDouble(), y1.toDouble(), width.toDouble(), y1.toDouble())
    }

    private fun topNeeded(): Boolean {
        return state != MAXIMIZED_VERT && state != MAXIMIZED_BOTH
    }

    override fun getBorderInsets(c: Component): Insets {
        val scale = JBUI.scale(THICKNESS)
        return if (topNeeded()) Insets(THICKNESS, 0, scale, 0) else Insets(0, 0, scale, 0)
    }

    override fun isBorderOpaque(): Boolean {
        return true
    }
}*/
