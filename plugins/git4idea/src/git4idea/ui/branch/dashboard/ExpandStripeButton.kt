// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.*
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicGraphicsUtils

class ExpandStripeButton(internal val text: () -> String? = { null }, icon: Icon? = null) : JButton(icon) {
  init {
    isRolloverEnabled = true
    border = JBUI.Borders.empty()
  }

  override fun updateUI() {
    setUI(ExpandStripeButtonUI())
    isOpaque = false
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
  }
}

private class ExpandStripeButtonUI : BasicButtonUI() {
  private val myIconRect = Rectangle()
  private val myTextRect = Rectangle()
  private val myViewRect = Rectangle()
  private var ourViewInsets: Insets = JBInsets.emptyInsets()

  override fun getMinimumSize(c: JComponent): Dimension = getPreferredSize(c)
  override fun getMaximumSize(c: JComponent): Dimension = getPreferredSize(c)
  override fun getPreferredSize(c: JComponent): Dimension = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE

  override fun update(g: Graphics, c: JComponent) {
    val button = c as ExpandStripeButton
    val text = button.text()
    val icon = if (button.isEnabled) button.icon else button.disabledIcon
    if (icon == null && text == null) {
      return
    }
    val fm = button.getFontMetrics(button.font)
    ourViewInsets = c.getInsets(ourViewInsets)
    myViewRect.x = ourViewInsets.left
    myViewRect.y = ourViewInsets.top

    // Use inverted height & width
    myViewRect.height = c.getWidth() - (ourViewInsets.left + ourViewInsets.right)
    myViewRect.width = c.getHeight() - (ourViewInsets.top + ourViewInsets.bottom)

    myIconRect.height = 0
    myIconRect.width = myIconRect.height
    myIconRect.y = myIconRect.width
    myIconRect.x = myIconRect.y
    myTextRect.height = 0
    myTextRect.width = myTextRect.height
    myTextRect.y = myTextRect.width
    myTextRect.x = myTextRect.y

    val clippedText = SwingUtilities.layoutCompoundLabel(
      c, fm, text, icon,
      SwingConstants.CENTER, SwingConstants.RIGHT, SwingConstants.CENTER, SwingConstants.TRAILING,
      myViewRect, myIconRect, myTextRect,
      if (text == null) 0 else button.iconTextGap
    )

    // Paint button's background
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      val model = button.model
      myIconRect.x -= JBUIScale.scale(2)
      myTextRect.x -= JBUIScale.scale(2)

      g2.color = if (model.isRollover || model.isPressed) HOVER_BACKGROUND_COLOR else c.background
      g2.fillRect(0, 0, c.width, c.height)

      icon?.paintIcon(c, g2, myIconRect.y, JBUIScale.scale(2 * button.iconTextGap))
      g2.rotate(-Math.PI / 2)
      g2.translate(-c.getHeight() - 2 * myIconRect.width, 0)

      // paint text
      setupAntialiasing(g2)
      if (text != null) {
        if (model.isEnabled) {
          /* paint the text normally */
          g2.color = c.getForeground()
        }
        else {
          /* paint the text disabled ***/
          g2.color = UIManager.getColor("Button.disabledText")
        }
        BasicGraphicsUtils.drawString(g2, clippedText, 0, myTextRect.x, myTextRect.y + fm.ascent)
      }
    }
    finally {
      g2.dispose()
    }
  }

  companion object {
    private val HOVER_BACKGROUND_COLOR: Color =
      JBColor.namedColor("ToolWindow.Button.hoverBackground", JBColor(Gray.x55.withAlpha(40), Gray.x0F.withAlpha(40)))
  }
}