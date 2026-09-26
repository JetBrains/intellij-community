// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.inlay

import com.intellij.ide.ui.UISettings
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel

internal class EditorInlayStyle(
  private val arc: Int,
  private val borderWidth: Int,
  private val borderColor: Color,
  private val background: Color,
  private val margin: Insets,
  private val minimumSize: Dimension?,
  private val preferredSize: Dimension?,
) {
  fun applyTo(panel: JComponent) {
    panel.border = JBUI.Borders.empty(margin.top, margin.left, margin.bottom, margin.right)
    minimumSize?.let { panel.minimumSize = it }
    preferredSize?.let { panel.preferredSize = it }
  }

  fun createCard(): JPanel {
    val card = RoundedCardPanel(arc, borderWidth)
    card.background = background
    card.border = RoundedLineBorder(borderColor, arc, borderWidth)
    return card
  }
}

@ApiStatus.Internal
class EditorInlayStyleBuilder internal constructor() {
  var arc: Int = islandArc()
  var borderWidth: Int = islandBorderWidth()
  var borderColor: Color = JBColor.border()

  var background: Color? = null
  var margin: Insets = JBUI.insets(4, 8)
  var minimumSize: Dimension? = null
  var preferredSize: Dimension? = null

  fun island() {
    arc = islandArc()
    borderWidth = islandBorderWidth()
  }

  fun flat() {
    arc = 0
    borderWidth = JBUI.scale(1)
  }

  internal fun build(defaultBackground: Color): EditorInlayStyle = EditorInlayStyle(
    arc = arc,
    borderWidth = borderWidth,
    borderColor = borderColor,
    background = background ?: defaultBackground,
    margin = margin,
    minimumSize = minimumSize,
    preferredSize = preferredSize,
  )
}

private class RoundedCardPanel(
  private val arc: Int,
  private val borderWidth: Int,
) : JPanel(BorderLayout()) {
  init {
    isOpaque = false
  }

  override fun paintComponent(graphics: Graphics) {
    val graphics2D = graphics.create() as Graphics2D
    try {
      graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      graphics2D.color = background
      graphics2D.fillRoundRect(0, 0, width, height, arc, arc)
    }
    finally {
      graphics2D.dispose()
    }
  }

  override fun paintChildren(graphics: Graphics) {
    val graphics2D = graphics.create() as Graphics2D
    try {
      val inset = borderWidth.toFloat()
      val innerWidth = width - inset * 2
      val innerHeight = height - inset * 2
      if (innerWidth <= 0 || innerHeight <= 0) return

      val innerArc = (arc - borderWidth * 2).coerceAtLeast(0).toFloat()
      graphics2D.clip(RoundRectangle2D.Float(inset, inset, innerWidth, innerHeight, innerArc, innerArc))
      super.paintChildren(graphics2D)
    }
    finally {
      graphics2D.dispose()
    }
  }
}

private fun islandArc(): Int =
  JBUI.scale(JBUI.getInt(if (UISettings.getInstance().compactMode) "Island.arc.compact" else "Island.arc", 16))

private fun islandBorderWidth(): Int {
  val compactMode = UISettings.getInstance().compactMode
  return JBUI.scale(JBUI.getInt(if (compactMode) "Island.borderWidth.compact" else "Island.borderWidth", if (compactMode) 4 else 6))
}
