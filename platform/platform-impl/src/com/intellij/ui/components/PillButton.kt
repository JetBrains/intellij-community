// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.DrawUtil
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

private val TEXT_GAPS: UnscaledGaps = UnscaledGaps(top = 4, left = 16, bottom = 3, right = 16)
private const val DEFAULT_FONT_SIZE: Int = 13
private const val BORDER_SIZE: Float = 1.5f

private val DISABLED_FOREGROUND: Color = JBUI.CurrentTheme.Label.disabledForeground()
private val DISABLED_BORDER_COLOR: Color = JBUI.CurrentTheme.Button.disabledOutlineColor()

@ApiStatus.Internal
class PillButton(text: @NlsContexts.Button String? = null) : JComponent() {

  companion object {
    @JvmField
    val BLUE: ColorState = object : ColorState {
      override val foreground: Color
        get() = JBColor.namedColor("PillButton.blueForeground", 0x2F5EB9, 0x71A1FE)

      override val background: Color
        get() = JBColor.namedColor("PillButton.blueBackground") // Default transparent

      override val borderColor: Color
        get() = JBColor.namedColor("PillButton.blueBorderColor", 0x538AF9, 0x538AF9)

      override val hoverForeground: Color
        get() = JBColor.namedColor("PillButton.blueHoverForeground", 0x2F5EB9, 0x71A1FE)

      override val hoverBackground: Color
        get() = JBColor.namedColor("PillButton.blueHoverBackground", 0xE3EBFE, 0x233558)

      override val hoverBorderColor: Color
        get() = JBColor.namedColor("PillButton.blueHoverBorderColor", 0x538AF9, 0x538AF9)
    }
  }

  interface ColorState {
    val foreground: Color?
    val background: Color?
    val borderColor: Color?
    val hoverForeground: Color?
    val hoverBackground: Color?
    val hoverBorderColor: Color?
  }

  var text: @NlsContexts.Button String? = null
    set(value) {
      if (field != value) {
        field = value
        revalidate()
        repaint()
      }
    }

  private var hovered = false
  private var colorState: ColorState = BLUE

  /**
   * Config colors with theme values
   */
  fun setColorState(colorState: ColorState) {
    if (colorState != this.colorState) {
      this.colorState = colorState
      applyColors()
    }
  }

  private fun applyColors() {
    foreground = colorState.foreground
    background = colorState.background

    repaint()
  }

  init {
    this.text = text
    isOpaque = false
    font = JBFont.regular()

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

  override fun updateUI() {
    super.updateUI()

    applyColors()
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)

    if (!enabled) {
      // Disabled components don't receive mouseExited
      hovered = false
    }
  }

  override fun paintComponent(g: Graphics) {
    val rect = Rectangle(0, 0, width, height)
    val arc = height.toFloat()
    val g2 = g.create() as Graphics2D

    try {
      DrawUtil.setupRenderingHints(g2)

      val borderColor = when {
        !isEnabled -> DISABLED_BORDER_COLOR
        hovered -> colorState.hoverBorderColor
        else -> colorState.borderColor
      }
      val background = when {
        !isEnabled -> null
        hovered -> colorState.hoverBackground
        else -> background
      }
      if (borderColor == null || borderColor.alpha == 0 || background == borderColor) {
        // Don't paint border separately
        background?.let {
          DrawUtil.fillRoundedRectangle(g2, rect, it, arc)
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

        g2.color = when {
          !isEnabled -> DISABLED_FOREGROUND
          hovered -> colorState.hoverForeground
          else -> foreground
        }
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
    val font = font ?: return Dimension(0, JBUI.scale(DEFAULT_FONT_SIZE))

    val text = text
    val fontMetrics = getFontMetrics(font)
    val width = if (text == null) 0 else fontMetrics.stringWidth(text)

    return Dimension(width, fontMetrics.height)
  }
}
