package com.intellij.extendedToolWindowsUi

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.openapi.wm.impl.SquareStripeButtonLookExtension
import com.intellij.openapi.wm.impl.ToolWindowAnchorEnum
import com.intellij.openapi.wm.impl.getAnchorEnum
import com.intellij.toolWindow.StripeButtonUi
import com.intellij.toolWindow.ToolWindowExtension
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.UIManager

internal class ToolWindowExtensionImpl : ToolWindowExtension {

  override fun isStripeResizable(): Boolean {
    return false
  }

  override fun isToolWindowNameVisible(): Boolean {
    return true
  }

  override fun getStripeIconUnscaledSize(): Int {
    return 16
  }

  override fun getStripeButtonUnscaledSize(): Int {
    return if (compactMode) 28 else 32
  }

  override fun createSquareStripeButtonLook(button: SquareStripeButton): SquareStripeButtonLook {
    return SquareStripeButtonLookVerticalText(button)
  }
}

private val compactMode: Boolean
  get() = UISettings.getInstance().compactMode

private class SquareStripeButtonLookVerticalText(button: SquareStripeButton) : SquareStripeButtonLookExtension(button) {

  private fun getForegroundColor(): Color {
    return if (toolWindow.isActive) StripeButtonUi.SELECTED_FOREGROUND_COLOR else StripeButtonUi.FOREGROUND_COLOR
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val anchorEnum = toolWindow.getAnchorEnum()
    val labelWidth = getLabelWidth()
    val scaledInsets = getButtonScaledInsets()

    // because SquareStripeButtonLook doesn't know about name and pref size, we need to do some trick for right icon layout
    val buttonWrapper = object : ActionButtonComponent {
      override fun getPopState() = actionButton!!.popState

      override fun getWidth() = actionButton!!.width

      override fun getHeight(): Int {
        return button.getPreferredSize().height - scaledInsets.fullWidth - labelWidth
      }

      override fun getInsets() = actionButton!!.insets
    }
    val color = UIManager.getColor("ToolWindow.Button.selectedForeground")
    val renderedIcon = if (!toolWindow.isActive || color == null) icon else toStrokeIcon(icon, color)
    val iconPosition = getIconPosition(buttonWrapper, renderedIcon)

    when (anchorEnum) {
      ToolWindowAnchorEnum.LEFT -> {
        iconPosition.y += scaledInsets.leftRightExtraInset + scaledInsets.iconLabelInset + labelWidth
      }
      ToolWindowAnchorEnum.RIGHT -> {
        iconPosition.y += scaledInsets.leftRightExtraInset
      }
      ToolWindowAnchorEnum.TOP,
      ToolWindowAnchorEnum.BOTTOM,
        -> {
      }
    }

    super.paintIcon(g, buttonWrapper, renderedIcon, iconPosition.x, iconPosition.y)

    val f = getTextFont()
    val fm = button.getFontMetrics(f)
    val text = getStripeText()

    UIUtil.useSafely(g!!) { g2 ->
      g2.color = getForegroundColor()
      g2.font = f
      UISettings.setupAntialiasing(g2)

      when (anchorEnum) {
        ToolWindowAnchorEnum.LEFT -> {
          g2.rotate(-Math.PI / 2)
          val iconCenterX = iconPosition.x + renderedIcon.iconWidth / 2
          val baselineX = iconCenterX + (fm.ascent - fm.descent) / 2
          val textBottomY = iconPosition.y - scaledInsets.iconLabelInset
          g2.drawString(text, -textBottomY, baselineX)
        }
        ToolWindowAnchorEnum.RIGHT -> {
          g2.rotate(Math.PI / 2)
          val iconCenterX = iconPosition.x + renderedIcon.iconWidth / 2
          val baselineX = iconCenterX - (fm.ascent - fm.descent) / 2
          val textTopY = iconPosition.y + renderedIcon.iconHeight + scaledInsets.iconLabelInset
          g2.drawString(text, textTopY, -baselineX)
        }
        ToolWindowAnchorEnum.TOP -> {
          // todo
        }
        ToolWindowAnchorEnum.BOTTOM -> {
          // todo
        }
      }
    }
  }

  override fun getPreferredSize(size: Dimension): Dimension {
    val scaledInsets = getButtonScaledInsets()
    size.height += scaledInsets.fullWidth + getLabelWidth()
    return size
  }

  private fun getLabelWidth(): Int {
    return UIUtil.computeStringWidth(button, button.getFontMetrics(getTextFont()), getStripeText())
  }

  private fun getTextFont() = button.font

  private fun getStripeText(): String {
    return (toolWindow.stripeShortTitleProvider?.get() ?: toolWindow.stripeTitleProvider.get()).trim()
  }

  private fun getButtonScaledInsets(): ButtonScaledInsets {
    return if (compactMode)
      ButtonScaledInsets(leftRightExtraInset = JBUIScale.scale(2), iconLabelInset = JBUIScale.scale(4))
    else ButtonScaledInsets(leftRightExtraInset = JBUIScale.scale(4), iconLabelInset = JBUIScale.scale(6))
  }

  private data class ButtonScaledInsets(val leftRightExtraInset: Int, val iconLabelInset: Int) {
    val fullWidth: Int
      get() = leftRightExtraInset * 2 + iconLabelInset
  }
}
