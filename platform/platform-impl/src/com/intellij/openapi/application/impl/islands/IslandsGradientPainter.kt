// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ProjectWidgetGradientLocationService
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.GradientTextureCache
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeGlassPaneEx
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.PaintUtil.alignIntToInt
import com.intellij.ui.paint.PaintUtil.alignTxToInt
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * The list of auto replaced colors. Should contain only very specific colors, don't add widely used like `Panel.background`
 */
private val islandsGradientColors = setOf(
  // Root components
  "MainWindow.background",
  "MainToolbar.background",
  "MainToolbar.inactiveBackground",
  "ToolWindow.Stripe.background",
  "StatusBar.background",
)

private val islandsToolWindowGradientColors = setOf(
  "ToolWindow.background",
  "ToolWindow.header.background",
  "ToolWindow.Header.inactiveBackground",
)

internal fun isIslandsGradientColor(paint: Paint?): Boolean {
  val colorName = (paint as? JBColor)?.name ?: return false
  if (isColorIslandGradient() && colorName in islandsToolWindowGradientColors) {
    return true
  }
  return colorName in islandsGradientColors
}

private fun isIslandsToolWindowGradientColor(paint: Paint?): Boolean {
  val colorName = (paint as? JBColor)?.name ?: return false
  return colorName in islandsToolWindowGradientColors
}

internal class IslandsGradientPainter(private val frame: IdeFrame, private val mainColor: Color, private val enabled: () -> Boolean) : AbstractPainter() {

  private val projectWindowCustomizer = ProjectWindowCustomizerService.getInstance()

  private var doPaint = true

  override fun needsRepaint(): Boolean = enabled()

  override fun executePaint(component: Component, g: Graphics2D) {
    if (doPaint) {
      try {
        doPaint = false

        if (isIslandsGradientColor(g.paint)) {
          islandsGradientPaint(frame, mainColor, projectWindowCustomizer, component, g)
        }
      }
      finally {
        doPaint = true
      }
    }
  }
}

internal fun islandsGradientPaint(frame: IdeFrame, mainColor: Color, projectWindowCustomizer: ProjectWindowCustomizerService, component: Component, g: Graphics2D) {
  if (CustomWindowHeaderUtil.isCompactHeader()) {
    return
  }
  if (component is IdeGlassPaneEx && !component.isColorfulToolbar) {
    return
  }

  val project = frame.project ?: return

  if (isColorIslandGradient()) {
    doColorGradientPaint(project, projectWindowCustomizer, component, g)
  }
  else {
    doGradientPaint(frame, mainColor, project, projectWindowCustomizer, component, g)
  }
}

internal fun isColorIslandGradient(): Boolean = Registry.`is`("idea.islands.color.gradient.enabled", false)

// TODO: replace isRoundedTabDuringDrag to publuc API to check islands theme
internal fun isColorIslandGradientAvailable(): Boolean = isColorIslandGradient() && InternalUICustomization.getInstance()?.isRoundedTabDuringDrag == true

private fun doGradientPaint(frame: IdeFrame, mainColor: Color, project: Project, projectWindowCustomizer: ProjectWindowCustomizerService,
                            component: Component, g: Graphics2D) {
  val centerX = project.service<ProjectWidgetGradientLocationService>().gradientOffsetRelativeToRootPane

  val ctx = ScaleContext.create(g)

  val length = JBUI.getInt("RecentProject.MainToolbarGradient.width", 700)
  val height = JBUI.getInt("RecentProject.MainToolbarGradient.height", 200)

  val leftWidth = alignIntToInt(centerX.toInt(), ctx, PaintUtil.RoundingMode.CEIL, null)
  val rightWidth = alignIntToInt(length, ctx, PaintUtil.RoundingMode.CEIL, null)
  val totalWidth = alignIntToInt(leftWidth + rightWidth, ctx, PaintUtil.RoundingMode.CEIL, null)

  val fullBounds = Rectangle(totalWidth, height)
  val bounds = g.clipBounds?.intersection(fullBounds) ?: fullBounds
  if (bounds.isEmpty) {
    return
  }

  val cache = getGradientCache(frame.component, "GradientCache")
  val centerColor = projectWindowCustomizer.getGradientProjectColor(project)
  val blendedColor = cache.getBlendedColor(mainColor, centerColor)

  val leftGradientTexture = cache.left.getHorizontalTexture(g, leftWidth, mainColor, blendedColor)
  val rightGradientTexture = cache.right.getHorizontalTexture(g, rightWidth, blendedColor, mainColor, leftWidth)
  val initialComposite = g.composite
  val islandsInactiveFrameGraphics2D = g as? IslandsInactiveFrameGraphics2D

  if (SwingUtilities.getWindowAncestor(frame.component)?.isActive == false) {
    islandsInactiveFrameGraphics2D?.preserveComposite = true

    val componentFullBounds = Rectangle(component.width, component.height)
    val componentBounds = g.clipBounds?.intersection(componentFullBounds) ?: componentFullBounds
    if (!componentBounds.isEmpty) {
      g.color = mainColor
      g.fillRect(componentBounds.x, componentBounds.y, componentBounds.width, componentBounds.height)
    }

    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, islandsInactiveAlpha)
  }

  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

  alignTxToInt(g, null, true, false, PaintUtil.RoundingMode.FLOOR)

  val leftBounds = bounds.intersection(Rectangle(leftWidth, height))
  if (!leftBounds.isEmpty) {
    g.paint = leftGradientTexture
    g.fillRect(leftBounds.x, leftBounds.y, leftBounds.width, leftBounds.height)
  }

  val rightBounds = bounds.intersection(Rectangle(leftWidth, 0, rightWidth, height))
  if (!rightBounds.isEmpty) {
    g.paint = rightGradientTexture
    g.fillRect(rightBounds.x, rightBounds.y, rightBounds.width, rightBounds.height)
  }

  alignTxToInt(g, null, false, true, PaintUtil.RoundingMode.FLOOR)

  g.composite = initialComposite

  val startColor = if (SystemInfo.isMac) Gray.TRANSPARENT else ColorUtil.toAlpha(mainColor, 0)
  g.paint = GradientPaint(0f, 0f, startColor, 0f, height.toFloat(), mainColor)
  g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)

  islandsInactiveFrameGraphics2D?.preserveComposite = false
}

private class GradientCache {
  val left = GradientTextureCache()
  val right = GradientTextureCache()

  private var mainRgb = 0
  private var centerRgb = 0
  private var blendedColor: Color? = null

  fun getBlendedColor(mainColor: Color, centerColor: Color): Color {
    if (blendedColor == null || mainRgb != mainColor.rgb || centerRgb != centerColor.rgb) {
      mainRgb = mainColor.rgb
      centerRgb = centerColor.rgb
      blendedColor = ColorUtil.blendColorsInRgb(mainColor, centerColor, 0.85 * (centerColor.alpha.toDouble() / 255))
    }
    return blendedColor!!
  }
}

private fun getGradientCache(root: JComponent, key: String): GradientCache {
  val gradientCache = root.getClientProperty(key)
  if (gradientCache is GradientCache) {
    return gradientCache
  }

  val newValue = GradientCache()
  root.putClientProperty(key, newValue)
  return newValue
}

private fun doColorGradientPaint(project: Project, projectWindowCustomizer: ProjectWindowCustomizerService, component: Component, g: Graphics2D) {
  val islandsInactiveFrameGraphics2D = g as? IslandsInactiveFrameGraphics2D
  val initialComposite = g.composite
  val info = projectWindowCustomizer.getProjectGradients(project)

  if (isIslandsToolWindowGradientColor(g.paint)) {
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, JBUI.getFloat("Island.toolWindowAlpha", 0.2f))
  }
  else if (SwingUtilities.getWindowAncestor(component)?.isActive == false) {
    islandsInactiveFrameGraphics2D?.preserveComposite = true

    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, islandsInactiveAlpha)
  }

  val width = component.width
  val height = component.height
  val widthF = width.toFloat()
  val heightF = height.toFloat()

  g.paint = LinearGradientPaint(0f, 0f, widthF, heightF,
                                floatArrayOf(info.getDiagonalFraction1(0f), info.getDiagonalFraction2(0.13f),
                                             info.getDiagonalFraction3(0.3f), info.getDiagonalFraction4(1f)),
                                arrayOf(info.diagonalColor1, info.diagonalColor2, info.diagonalColor3, info.diagonalColor4))

  g.fillRect(0, 0, width, height)

  val ovalRadius = width / 4f
  val ovalWidth = width / 2
  val ovalCenterX = width * 0.2f
  val ovalCenterY = 36f

  g.paint = RadialGradientPaint(ovalCenterX, ovalCenterY, ovalRadius,
                                floatArrayOf(0f, 1f),
                                arrayOf(info.radialColor1, info.radialColor2))

  g.fillOval((ovalCenterX - ovalRadius).toInt(), (ovalCenterY - ovalRadius).toInt(), ovalWidth, ovalWidth)

  g.paint = GradientPaint(0f, 0f, info.horizontalColor1, widthF, 0f, info.horizontalColor2)

  g.fillRect(0, 0, width, height)

  g.paint = GradientPaint(0f, 0f, info.verticalColor1, 0f, heightF, info.verticalColor2)

  g.fillRect(0, 0, width, height)

  g.composite = initialComposite
  islandsInactiveFrameGraphics2D?.preserveComposite = false
}
