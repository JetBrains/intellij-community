package com.intellij.settingsSync.core.config

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D

class RoundedBorderLayoutPanel(
  hgap: Int,
  vgap: Int,
  backgroundColor: Color,
  borderOffset: Int,
  private val borderColor: Color,
  private val borderThickness: Int = 1,
  private val cornerRadius: Int = 12,
) : BorderLayoutPanel(hgap, vgap) {

  init {
    isOpaque = true
    background = backgroundColor
    border = JBUI.Borders.empty(borderOffset)
  }

  override fun paintComponent(g: Graphics) {
    val scaledCornerRadius = JBUI.scale(cornerRadius)
    val scaledBorderThickness = JBUI.scale(borderThickness)

    g.color = background
    val config = GraphicsUtil.setupAAPainting(g)

    g.fillRoundRect(0, 0, width, height, scaledCornerRadius, scaledCornerRadius)

    val g2d = g as Graphics2D
    g2d.color = borderColor
    g2d.stroke = BasicStroke(scaledBorderThickness.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g2d.drawRoundRect(0, 0, width - scaledBorderThickness, height - scaledBorderThickness, scaledCornerRadius, scaledCornerRadius)

    config.restore()
  }
}