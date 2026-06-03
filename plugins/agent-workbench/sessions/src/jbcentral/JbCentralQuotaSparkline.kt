// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent
import kotlin.math.max

/** A tiny per-day spend sparkline: one vertical bar per [DailyUsagePoint], left to right. */
internal class JbCentralQuotaSparkline(private val points: List<DailyUsagePoint>) : JComponent() {
  // Pale neutral gray, matching the status-bar quota progress bar.
  private val barColor: Color = JBColor(0x6C707E, 0x868A91)

  init {
    isOpaque = false
    preferredSize = Dimension(max(points.size, 1) * JBUI.scale(6), JBUI.scale(28))
  }

  override fun paintComponent(g: Graphics) {
    val maxSpent = points.maxOfOrNull { it.spentUsd } ?: 0.0
    if (points.isEmpty() || maxSpent <= 0.0) return

    GraphicsUtil.setupAAPainting(g)
    g.color = barColor

    val gap = JBUI.scale(2)
    val barWidth = JBUI.scale(4)
    val step = barWidth + gap
    val h = height
    points.forEachIndexed { index, point ->
      val barHeight = max(JBUI.scale(1), (point.spentUsd / maxSpent * h).toInt())
      val x = index * step
      g.fillRect(x, h - barHeight, barWidth, barHeight)
    }
  }
}
