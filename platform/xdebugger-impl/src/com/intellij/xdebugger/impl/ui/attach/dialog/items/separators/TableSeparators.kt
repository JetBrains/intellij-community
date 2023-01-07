package com.intellij.xdebugger.impl.ui.attach.dialog.items.separators

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.RelativeFont
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.max

abstract class TableGroupHeaderSeparator(private val hideLine: Boolean) : SeparatorWithText() {
  private var myLabelInsets: Insets = JBUI.insets(3, 18)
  private var baseLineInsets: Insets

  init {
    if (ExperimentalUI.isNewUI()) {
      baseLineInsets = JBUI.CurrentTheme.Popup.separatorInsets()
      border = JBUI.Borders.empty()
      font = RelativeFont.BOLD.derive(JBFont.small())
    }
    else {
      baseLineInsets = JBUI.insets(getVgap(), getHgap(), getVgap(), getHgap())
    }
  }

  override fun getInsets(): Insets = JBInsets.emptyInsets()

  protected abstract fun getModifiedLineInsets(baseLineInsets: Insets): Insets

  override fun getPreferredElementSize(): Dimension? {
    val size: Dimension = if (caption == null) {
      Dimension(max(myPrefWidth, 0), 0)
    }
    else {
      getLabelSize(myLabelInsets)
    }
    val lineInsets = getModifiedLineInsets(baseLineInsets)
    if (!hideLine) size.height += lineInsets.top + lineInsets.bottom + 1
    JBInsets.addTo(size, insets)
    return size
  }

  override fun paintComponent(g: Graphics) {
    g.color = foreground
    val bounds = Rectangle(width, height)
    //JBInsets.removeFrom(bounds, insets)
    val lineInsets = getModifiedLineInsets(baseLineInsets)
    if (!hideLine) {
      paintLine(g, bounds)
      val lineHeight = lineInsets.top + lineInsets.bottom + 1
      bounds.y += lineHeight
      bounds.height -= lineHeight
    }
    val caption = caption
    if (caption != null) {
      bounds.x += myLabelInsets.left
      bounds.width -= myLabelInsets.left + myLabelInsets.right
      bounds.y += myLabelInsets.top
      bounds.height -= myLabelInsets.top + myLabelInsets.bottom
      val iconR = Rectangle()
      val textR = Rectangle()
      val fm = g.fontMetrics
      val label = SwingUtilities.layoutCompoundLabel(fm, caption, null, SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.CENTER,
                                                     SwingConstants.LEFT, bounds, iconR, textR, 0)
      setupAntialiasing(g)
      g.color = textForeground
      g.drawString(label, textR.x, textR.y + fm.ascent)
    }
  }

  private fun paintLine(g: Graphics, bounds: Rectangle) {
    val lineInsets = getModifiedLineInsets(baseLineInsets)
    val x = bounds.x + lineInsets.left
    val width = bounds.width - lineInsets.left - lineInsets.right
    val y = bounds.y + lineInsets.top
    RectanglePainter.FILL.paint((g as Graphics2D), x, y, width, 1, null)
  }
}

class TableGroupHeaderFirstColumnSeparator(title: String?, hideLine: Boolean) : TableGroupHeaderSeparator(hideLine) {
  init {
    caption = title
    setCaptionCentered(false)
  }

  override fun getModifiedLineInsets(baseLineInsets: Insets): Insets {
    return JBInsets(baseLineInsets.top, baseLineInsets.left, baseLineInsets.bottom, 0)
  }
}

class TableGroupHeaderColumnSeparator(hideLine: Boolean) : TableGroupHeaderSeparator(hideLine) {
  override fun getModifiedLineInsets(baseLineInsets: Insets): Insets {
    return JBInsets(baseLineInsets.top, 0, baseLineInsets.bottom, 0)
  }
}


class TableGroupHeaderLastColumnSeparator(hideLine: Boolean) : TableGroupHeaderSeparator(hideLine) {
  override fun getModifiedLineInsets(baseLineInsets: Insets): Insets {
    return JBInsets(baseLineInsets.top, 0, baseLineInsets.bottom, baseLineInsets.right)
  }
}