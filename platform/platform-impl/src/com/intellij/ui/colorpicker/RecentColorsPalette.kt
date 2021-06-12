// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.colorpicker

/**
 * @author Konstantin Bulenkov
 */
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private const val COLOR_BUTTON_ROW = 2
private const val COLOR_BUTTON_COLUMN = 10

class RecentColorsPalette(private val pickerModel: ColorPickerModel,
                          private val recentColors: List<Color>) : JPanel() {

  @get:TestOnly
  val colorButtons = Array(COLOR_BUTTON_ROW * COLOR_BUTTON_COLUMN) {
    ColorButton(recentColors.getOrElse(it) { _ -> Color.WHITE }).apply {
      background = PICKER_BACKGROUND_COLOR
      addActionListener { pickerModel.setColor(color, this@RecentColorsPalette) }
    }
  }

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = JBUI.Borders.empty(5, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 10, HORIZONTAL_MARGIN_TO_PICKER_BORDER)
    background = PICKER_BACKGROUND_COLOR

    val colorButtonPanel = JPanel(GridLayout(COLOR_BUTTON_ROW, COLOR_BUTTON_COLUMN)).apply {
      border = JBUI.Borders.empty()
      background = PICKER_BACKGROUND_COLOR
      colorButtons.forEach { add(it) }
    }
    add(colorButtonPanel)
  }
}


private val COLOR_BUTTON_INNER_BORDER_COLOR = JBColor(Color(0, 0, 0, 26), Color(255, 255, 255, 26))

class ColorButton(var color: Color = Color.WHITE): JButton() {
  private val FOCUS_BORDER_WIDTH = JBUI.scale(3)
  private val ROUND_CORNER_ARC = JBUI.scale(5)

  enum class Status { NORMAL, HOVER, PRESSED }

  var status = Status.NORMAL

  init {
    preferredSize = JBUI.size(28)
    border = JBUI.Borders.empty(6)
    isRolloverEnabled = true
    hideActionText = true
    background = PICKER_BACKGROUND_COLOR

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        status = Status.HOVER
        repaint()
      }

      override fun mouseExited(e: MouseEvent) {
        status = Status.NORMAL
        repaint()
      }

      override fun mousePressed(e: MouseEvent) {
        status = Status.PRESSED
        repaint()
      }

      override fun mouseReleased(e: MouseEvent) {
        status = when (status) {
          Status.PRESSED -> Status.HOVER
          else -> Status.NORMAL
        }
        repaint()
      }
    })

    with (getInputMap(JComponent.WHEN_FOCUSED)) {
      put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed")
      put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released")
    }
  }

  override fun paintComponent(g: Graphics) {
    val g2d = g as Graphics2D
    val originalAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    val originalColor = g.color

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    // Cleanup background
    g.color = background
    g.fillRect(0, 0, width, height)


    if (status == Status.HOVER || status == Status.PRESSED) {
      val l = insets.left / 2
      val t = insets.top / 2
      val w = width - l - insets.right / 2
      val h = height - t - insets.bottom / 2

      val focusColor = UIUtil.getFocusedBoundsColor()
      g.color = if (status == Status.HOVER) focusColor else focusColor.darker()
      g2d.fillRoundRect(l, t, w, h, 7, 7)
    }
    else if (isFocusOwner) {
      val l = insets.left - FOCUS_BORDER_WIDTH
      val t = insets.top - FOCUS_BORDER_WIDTH
      val w = width - l - insets.right + FOCUS_BORDER_WIDTH
      val h = height - t - insets.bottom + FOCUS_BORDER_WIDTH

      g.color = UIUtil.getFocusedFillColor()
      g2d.fillRoundRect(l, t, w, h, 7, 7)
    }

    val left = insets.left
    val top = insets.top
    val brickWidth = width - insets.left - insets.right
    val brickHeight = height - insets.top - insets.bottom
    g.color = color
    g2d.fillRoundRect(left, top, brickWidth, brickHeight, ROUND_CORNER_ARC, ROUND_CORNER_ARC)
    g.color = COLOR_BUTTON_INNER_BORDER_COLOR
    g2d.drawRoundRect(left, top, brickWidth, brickHeight, ROUND_CORNER_ARC, ROUND_CORNER_ARC)

    g.color = originalColor
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing)
  }
}
