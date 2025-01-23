// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.trialState

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

@ApiStatus.Internal
class TrialStateButton : JComponent() {

  enum class ColorState(val foreground: Color, val background: Color, val borderColor: Color, val hoverBackground: Color) {
    DEFAULT(JBUI.CurrentTheme.TrialWidget.Default.FOREGROUND,
            JBUI.CurrentTheme.TrialWidget.Default.BACKGROUND,
            JBUI.CurrentTheme.TrialWidget.Default.BORDER_COLOR,
            JBUI.CurrentTheme.TrialWidget.Default.HOVER_BACKGROUND),
    ACTIVE(JBUI.CurrentTheme.TrialWidget.Active.FOREGROUND,
           JBUI.CurrentTheme.TrialWidget.Active.BACKGROUND,
           JBUI.CurrentTheme.TrialWidget.Active.BORDER_COLOR,
           JBUI.CurrentTheme.TrialWidget.Active.HOVER_BACKGROUND),
    ALERT(JBUI.CurrentTheme.TrialWidget.Alert.FOREGROUND,
          JBUI.CurrentTheme.TrialWidget.Alert.BACKGROUND,
          JBUI.CurrentTheme.TrialWidget.Alert.BORDER_COLOR,
          JBUI.CurrentTheme.TrialWidget.Alert.HOVER_BACKGROUND),
    EXPIRING(JBUI.CurrentTheme.TrialWidget.Expiring.FOREGROUND,
             JBUI.CurrentTheme.TrialWidget.Expiring.BACKGROUND,
             JBUI.CurrentTheme.TrialWidget.Expiring.BORDER_COLOR,
             JBUI.CurrentTheme.TrialWidget.Expiring.HOVER_BACKGROUND);
  }

  companion object {
    private val TEXT_GAPS = UnscaledGaps(top = 4, left = 16, bottom = 3, right = 16)
    private const val DEFAULT_FONT_SIZE = 13
    private const val BORDER_SIZE = 1.5f
  }

  var borderColor: Color? = null
  var hoverBackground: Color? = null
  var text: @NlsContexts.Button String? = null
    set(value) {
      if (field != value) {
        field = value
        revalidate()
        repaint()
      }
    }

  private var hovered = false

  /**
   * Config colors with theme values
   */
  fun setColorState(colorState: ColorState) {
    foreground = colorState.foreground
    background = colorState.background
    borderColor = colorState.borderColor
    hoverBackground = colorState.hoverBackground

    repaint()
  }

  init {
    isOpaque = false
    font = JBFont.regular().deriveFont(DEFAULT_FONT_SIZE.toFloat())
    setColorState(ColorState.DEFAULT)

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        hovered = true
        repaint()
      }

      override fun mouseExited(e: MouseEvent?) {
        hovered = false
        repaint()
      }
    })
  }

  override fun paint(g: Graphics?) {
    if (g == null) {
      return
    }

    val rect = Rectangle(0, 0, width, height)
    val arc = height.toFloat()
    val g2 = g.create() as Graphics2D

    try {
      DarculaNewUIUtil.setupRenderingHints(g2)

      val borderColor = borderColor
      val background = if (hovered) hoverBackground else background
      if (borderColor == null || borderColor.alpha == 0 || background == borderColor) {
        // Don't paint border separately
        background?.let {
          DarculaNewUIUtil.fillRoundedRectangle(g2, rect, it, arc)
        }
      }
      else {
        background?.let {
          DarculaNewUIUtil.fillInsideComponentBorder(g2, rect, it, arc)
        }
        DarculaNewUIUtil.drawRoundedRectangle(g2, rect, borderColor, arc, BORDER_SIZE)
      }

      text?.let {
        val fontMetrics = getFontMetrics(font)
        val offset = (rect.height - TEXT_GAPS.height - getFontMetrics(font).height) / 2

        g2.color = foreground
        g2.font = font
        g2.drawString(it, TEXT_GAPS.left, TEXT_GAPS.top + offset + fontMetrics.ascent)
      }
    }
    finally {
      g2.dispose()
    }
  }

  override fun getMinimumSize(): Dimension {
    return preferredSize
  }

  override fun getPreferredSize(): Dimension {
    val textDimension = getTextDimension()

    return Dimension(textDimension.width + TEXT_GAPS.width, textDimension.height + TEXT_GAPS.height)
  }

  private fun getTextDimension(): Dimension {
    val font = font ?: return Dimension(0, DEFAULT_FONT_SIZE)
    val text = text
    val fontMetrics = getFontMetrics(font)
    val width = if (text == null) 0 else fontMetrics.stringWidth(text)

    return Dimension(width, fontMetrics.height)
  }
}