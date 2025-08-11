// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ProjectWidgetGradientLocationService
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.GradientTextureCache
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.AbstractPainter
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

internal class IslandsGradientPainter(private val frame: IdeFrame, private val mainColor: Color, private val enabled: () -> Boolean) : AbstractPainter() {
  private val leftGradientCache: GradientTextureCache = GradientTextureCache()
  private val rightGradientCache: GradientTextureCache = GradientTextureCache()

  private val projectWindowCustomizer = ProjectWindowCustomizerService.getInstance()

  private var doPaint = true

  override fun needsRepaint(): Boolean = enabled()

  override fun executePaint(component: Component, g: Graphics2D) {
    if (doPaint) {
      try {
        doPaint = false
        doPaint(component, g)
      }
      finally {
        doPaint = true
      }
    }
  }

  private fun doPaint(component: Component, g: Graphics2D) {
    if (CustomWindowHeaderUtil.isCompactHeader()) {
      return
    }
    if (component is IdeGlassPaneEx && !component.isColorfulToolbar) {
      return
    }

    val project = frame.project ?: return

    val centerColor = projectWindowCustomizer.getGradientProjectColor(project)

    val centerX = project.service<ProjectWidgetGradientLocationService>().gradientOffsetRelativeToRootPane

    val blendedColor = ColorUtil.blendColorsInRgb(mainColor, centerColor, 0.85 * (centerColor.alpha.toDouble() / 255))

    val ctx = ScaleContext.create(g)

    val length = JBUI.getInt("RecentProject.MainToolbarGradient.width", 700)
    val height = JBUI.getInt("RecentProject.MainToolbarGradient.height", 200)

    val leftWidth = alignIntToInt(centerX.toInt(), ctx, PaintUtil.RoundingMode.CEIL, null)
    val rightWidth = alignIntToInt(length, ctx, PaintUtil.RoundingMode.CEIL, null)
    val totalWidth = alignIntToInt(leftWidth + rightWidth, ctx, PaintUtil.RoundingMode.CEIL, null)

    val leftGradientTexture = leftGradientCache.getHorizontalTexture(g, leftWidth, mainColor, blendedColor)
    val rightGradientTexture = rightGradientCache.getHorizontalTexture(g, rightWidth, blendedColor, mainColor, leftWidth)

    g.color = mainColor
    g.fillRect(0, 0, component.width, component.height)

    // TODO: check UISettings.getInstance().differentiateProjects

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
}