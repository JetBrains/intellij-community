// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ProjectWidgetGradientLocationService
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.GradientTextureCache
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeGlassPaneEx
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.PaintUtil.alignIntToInt
import com.intellij.ui.paint.PaintUtil.alignTxToInt
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent

internal class IslandsGradientPainter(private val frame: IdeFrame, private val mainColor: Color, private val enabled: () -> Boolean) : AbstractPainter() {
  private val projectWindowCustomizer = ProjectWindowCustomizerService.getInstance()

  private var doPaint = true

  override fun needsRepaint(): Boolean = enabled()

  override fun executePaint(component: Component, g: Graphics2D) {
    if (doPaint) {
      try {
        doPaint = false
        islandsGradientPaint(frame, mainColor, projectWindowCustomizer, component, g)
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

  if (Registry.`is`("idea.islands.color.gradient.enabled", false)) {
    doColorGradientPaint(project, projectWindowCustomizer, component, g)
  }
  else {
    doGradientPaint(frame, mainColor, project, projectWindowCustomizer, component, g)
  }
}

private fun doGradientPaint(frame: IdeFrame, mainColor: Color, project: Project, projectWindowCustomizer: ProjectWindowCustomizerService, component: Component, g: Graphics2D) {
  val centerColor = projectWindowCustomizer.getGradientProjectColor(project)

  val centerX = project.service<ProjectWidgetGradientLocationService>().gradientOffsetRelativeToRootPane

  val blendedColor = ColorUtil.blendColorsInRgb(mainColor, centerColor, 0.85 * (centerColor.alpha.toDouble() / 255))

  val ctx = ScaleContext.create(g)

  val length = JBUI.getInt("RecentProject.MainToolbarGradient.width", 700)
  val height = JBUI.getInt("RecentProject.MainToolbarGradient.height", 200)

  val leftWidth = alignIntToInt(centerX.toInt(), ctx, PaintUtil.RoundingMode.CEIL, null)
  val rightWidth = alignIntToInt(length, ctx, PaintUtil.RoundingMode.CEIL, null)
  val totalWidth = alignIntToInt(leftWidth + rightWidth, ctx, PaintUtil.RoundingMode.CEIL, null)

  val root = frame.component
  val leftGradientTexture = getGradientCache(root, "LeftGradientCache").getHorizontalTexture(g, leftWidth, mainColor, blendedColor)
  val rightGradientTexture = getGradientCache(root, "RightGradientCache").getHorizontalTexture(g, rightWidth, blendedColor, mainColor, leftWidth)

  g.color = mainColor
  g.fillRect(0, 0, component.width, component.height)

  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

  alignTxToInt(g, null, true, false, PaintUtil.RoundingMode.FLOOR)

  g.paint = leftGradientTexture
  g.fillRect(0, 0, leftWidth, height)

  g.paint = rightGradientTexture
  g.fillRect(leftWidth, 0, rightWidth, height)

  alignTxToInt(g, null, false, true, PaintUtil.RoundingMode.FLOOR)

  val startColor = if (ClientSystemInfo.isMac()) Gray.TRANSPARENT else ColorUtil.toAlpha(mainColor, 0)
  g.paint = GradientPaint(0f, 0f, startColor, 0f, height.toFloat(), mainColor)
  g.fillRect(0, 0, totalWidth, height)
}

private fun getGradientCache(root: JComponent, key: String): GradientTextureCache {
  val gradientCache = root.getClientProperty(key)
  if (gradientCache is GradientTextureCache) {
    return gradientCache
  }

  val newValue = GradientTextureCache()
  root.putClientProperty(key, newValue)
  return newValue
}

private fun doColorGradientPaint(project: Project, projectWindowCustomizer: ProjectWindowCustomizerService, component: Component, g: Graphics2D) {
  val info = projectWindowCustomizer.getProjectGradients(project)

  g.paint = LinearGradientPaint(0f, 0f, component.width.toFloat(), component.height.toFloat(),
                                floatArrayOf(info.getDiagonalFraction1(0f), info.getDiagonalFraction2(0.13f),
                                             info.getDiagonalFraction3(0.3f), info.getDiagonalFraction4(1f)),
                                arrayOf(info.diagonalColor1, info.diagonalColor2, info.diagonalColor3, info.diagonalColor4))

  g.fillRect(0, 0, component.width, component.height)

  val ovalRadius = component.width / 4f
  val ovalWidth = component.width / 2
  val ovalCenterX = component.width * 0.2f
  val ovalCenterY = 36f

  g.paint = RadialGradientPaint(ovalCenterX, ovalCenterY, ovalRadius,
                                floatArrayOf(0f, 1f),
                                arrayOf(info.radialColor1, info.radialColor2))

  g.fillOval((ovalCenterX - ovalRadius).toInt(), (ovalCenterY - ovalRadius).toInt(), ovalWidth, ovalWidth)

  g.paint = GradientPaint(0f, 0f, info.horizontalColor1, component.width.toFloat(), 0f, info.horizontalColor2)

  g.fillRect(0, 0, component.width, component.height)

  g.paint = GradientPaint(0f, 0f, info.verticalColor1, 0f, component.height.toFloat(), info.verticalColor2)

  g.fillRect(0, 0, component.width, component.height)
}