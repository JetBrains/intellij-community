// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.island

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.border.AbstractBorder

@ApiStatus.Experimental
@ApiStatus.Internal
open class XNextRoundedBorder protected constructor(
  private val fillColor: (c: JComponent) -> Paint?,
  private val borderColor: (c: JComponent) -> Paint?,
  private val emptyCornersGraphics: (g: Graphics, c: JComponent) -> Graphics?,
  private val emptyCornersColor: (c: JComponent) -> Paint?,
  private val arcDiameter: Int,
  private val thickness: Int,
  private val componentInsets: Insets = JBUI.emptyInsets(),
  private val outerInsets: Insets = JBUI.emptyInsets(),
) : AbstractBorder() {

  companion object {
    fun createIslandBorder(fillColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background }): XNextRoundedBorder {
      val borderColor = fillColor
      val emptyCornersGraphics = { g: Graphics, c: JComponent -> IdeBackgroundUtil.withEditorBackground(g, c) }
      val emptyCornersColor = { c: JComponent -> InternalUICustomization.getInstance()?.getCustomMainBackgroundColor() }
      val arcDiameter = 35
      val thickness: Int = JBUI.scale(2)
      val innerInsets: Insets = JBInsets(12, 12, 12, 12)
      val outerInsets: Insets = JBInsets(6, 5, 6, 5)

      return XNextRoundedBorder(fillColor, borderColor, emptyCornersGraphics, emptyCornersColor, arcDiameter, thickness, innerInsets, outerInsets)
    }

    fun createNewSolutionIslandBorder(fillColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background }, emptyCornersColor: (JComponent) -> Paint? = { c: JComponent -> c.parent?.background }): XNextRoundedBorder {
      val borderColor = fillColor
      val emptyCornersGraphics = { g: Graphics, c: JComponent -> IdeBackgroundUtil.withEditorBackground(g, c) }
      val arcDiameter = 30
      val thickness: Int = JBUI.scale(2)
      val innerInsets: Insets = JBInsets(12, 6, 12, 12)
      val outerInsets: Insets = JBInsets(6, 0, 0, 5)

      return XNextRoundedBorder(fillColor, borderColor, emptyCornersGraphics, emptyCornersColor, arcDiameter, thickness, innerInsets, outerInsets)
    }

    fun createNewSolutionAiChatBorder(fillColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background },
                                      borderColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background },
                                      emptyCornersColor: (JComponent) -> Paint? = { c: JComponent -> c.parent?.background }): XNextRoundedBorder {

      val emptyCornersGraphics = { g: Graphics, c: JComponent -> IdeBackgroundUtil.withEditorBackground(g, c) }
      val thickness: Int = JBUI.scale(2)
      val arcDiameter = 35
      val innerInsets: Insets = JBInsets(10, 25, 10, 15)
      val outerInsets: Insets = JBInsets(4, 10, 4, 10)

      return XNextRoundedBorder(fillColor, borderColor, emptyCornersGraphics, emptyCornersColor, arcDiameter, thickness, innerInsets, outerInsets)
    }

    fun createNewSolutionAiButton(fillColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background },
                                      borderColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background },
                                      emptyCornersColor: (JComponent) -> Paint? = { c: JComponent -> c.parent?.background }): XNextRoundedBorder {

      val emptyCornersGraphics = { g: Graphics, c: JComponent -> IdeBackgroundUtil.withEditorBackground(g, c) }
      val thickness: Int = JBUI.scale(1)
      val arcDiameter = 30

      return XNextRoundedBorder(fillColor, borderColor, emptyCornersGraphics, emptyCornersColor, arcDiameter, thickness)
    }

    fun createAiChatBorder(fillColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background }, borderColor: (c: JComponent) -> Paint? = { c: JComponent -> c.background }): XNextRoundedBorder {

      val emptyCornersGraphics = { g: Graphics, c: JComponent -> null }
      val emptyCornersColor: (c: JComponent) -> Paint? = { c: JComponent -> null }
      val thickness: Int = JBUI.scale(2)
      val arcDiameter = JBUI.scale(35)
      val innerInsets: Insets = JBInsets(5, 15, 5, 5)

      return XNextRoundedBorder(fillColor, borderColor, emptyCornersGraphics, emptyCornersColor, arcDiameter, thickness, innerInsets)
    }
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2d = g.create() as Graphics2D
    val g2dOriginal = IdeBackgroundUtil.getOriginalGraphics(g).create() as Graphics2D
    try {
      c as JComponent

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2dOriginal.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      val fill =fillColor(c) ?: c.background
      val border = borderColor(c)
      val emptyCorners = emptyCornersColor(c)

      val area = Area(Rectangle(0, 0, width, height))

      val offset = thickness / 2.0

      val borderShape = RoundRectangle2D.Double(outerInsets.left + x.toDouble() + offset,
                                                outerInsets.top + y.toDouble()+ offset,
                                                width.toDouble() - outerInsets.width - thickness - offset ,
                                                height.toDouble() - outerInsets.height - thickness - offset,
                                                arcDiameter.toDouble(),
                                                arcDiameter.toDouble())

      val islandShape = Area(borderShape)

      val componentShape = Area(RoundRectangle2D.Double(componentInsets.left + x.toDouble(),
                                                        componentInsets.top + y.toDouble(),
                                                        width.toDouble() - componentInsets.width,
                                                        height.toDouble() - componentInsets.height,
                                                        arcDiameter.toDouble(),
                                                        arcDiameter.toDouble()))



      area.subtract(islandShape)
      islandShape.subtract(componentShape)


      emptyCorners?.let { paint ->
        emptyCornersGraphics(g2d, c)?.let {
          val g2d_ = it as Graphics2D
          g2d_.paint = paint
          g2d_.fill(area)
        }
      }


      fill?.let {
        g2dOriginal.paint = it
        g2dOriginal.fill(islandShape)
      }

      border?.let { it ->
        g2dOriginal.paint = it
        g2dOriginal.stroke = BasicStroke(thickness.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2dOriginal.draw(borderShape)
      }

    }
    finally {
      g2d.dispose()
      g2dOriginal.dispose()
    }
  }

  override fun getBorderInsets(c: Component): Insets? {
    return componentInsets
  }
}

