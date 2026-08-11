package com.intellij.extendedToolWindowsUi

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.application.impl.islands.isIslandTheme
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.openapi.wm.impl.SquareStripeButtonLookExtension
import com.intellij.openapi.wm.impl.ToolWindowAnchorEnum
import com.intellij.openapi.wm.impl.isHorizontal
import com.intellij.openapi.wm.impl.toEnum
import com.intellij.toolWindow.StripeButtonUi
import com.intellij.toolWindow.extendedToolWindowsUi.ToolWindowExtension
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
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
    return ICON_UNSCALED_SIZE
  }

  override fun createSquareStripeButtonLook(button: SquareStripeButton): SquareStripeButtonLook {
    return SquareStripeButtonLookVerticalText(button)
  }

  override fun getIconPadding(toolbarAnchor: ToolWindowAnchorEnum): Insets {
    // Assume the paddings are symmetrical, so use the left padding as the reference
    // This allows customization and works well for both Islands and non-Islands themes
    val paddings = JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconPadding(true, false)
    val sidePadding = JBUIScale.scale(if (compactMode) 4 else 5)
    val left = paddings.left
    val right = paddings.right

    return when (toolbarAnchor) {
      ToolWindowAnchorEnum.LEFT ->
        @Suppress("UseDPIAwareInsets")
        Insets(sidePadding, left, sidePadding, right)

      ToolWindowAnchorEnum.RIGHT ->
        @Suppress("UseDPIAwareInsets")
        Insets(sidePadding, right, sidePadding, left) // Inverse

      ToolWindowAnchorEnum.TOP ->
        @Suppress("UseDPIAwareInsets")
        Insets(left, sidePadding, right, sidePadding)

      ToolWindowAnchorEnum.BOTTOM ->
        @Suppress("UseDPIAwareInsets")
        Insets(right, sidePadding, left, sidePadding)
    }
  }

  override fun getButtonMinSize(moreButton: Boolean): Dimension {
    val size = getStripeButtonUnscaledSize()

    val heightCorrection: Int
    if (moreButton) {
      // The visible part of More button should be square
      val padding = getIconPadding(ToolWindowAnchorEnum.LEFT) // should be the same correction with ToolWindowAnchorEnum.RIGHT
      heightCorrection = padding.height - padding.width
    }
    else {
      heightCorrection = 0
    }

    return JBDimension(size, size + heightCorrection)
  }

  private fun getStripeButtonUnscaledSize(): Int {
    var result = if (compactMode) 28 else 32
    if (!isIslandTheme()) {
      result += 3
    }
    return result
  }
}

private const val ICON_UNSCALED_SIZE = 16

private val compactMode: Boolean
  get() = UISettings.getInstance().compactMode

private class SquareStripeButtonLookVerticalText(button: SquareStripeButton) : SquareStripeButtonLookExtension(button) {

  private fun getForegroundColor(): Color {
    return if (toolWindow.isActive) StripeButtonUi.SELECTED_FOREGROUND_COLOR else StripeButtonUi.FOREGROUND_COLOR
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    if (actionButton == null) {
      return
    }

    val anchorEnum = toolWindow.anchor.toEnum()
    val isHorizontal = anchorEnum.isHorizontal()
    val labelWidth = getLabelWidth()
    val scaledInsets = getButtonScaledInsets()

    // because SquareStripeButtonLook doesn't know about name and pref size, we need to do some trick for right icon layout
    val buttonWrapper = object : ActionButtonComponent {
      override fun getPopState() = actionButton.popState

      override fun getWidth(): Int {
        return if (isHorizontal) button.getPreferredSize().width - scaledInsets.fullWidth - labelWidth else actionButton.width
      }

      override fun getHeight(): Int {
        return if (isHorizontal) actionButton.height else button.getPreferredSize().height - scaledInsets.fullWidth - labelWidth
      }

      override fun getInsets() = actionButton.insets
    }

    val color = UIManager.getColor("ToolWindow.Button.selectedForeground")
    val renderedIcon = if (!toolWindow.isActive || color == null) icon else toStrokeIcon(icon, color)

    // Avoid "dancing" for icons with badges, see com.intellij.openapi.wm.impl.SquareStripeButtonLook.getIconPosition
    val labelIconSize = JBUIScale.scale(ICON_UNSCALED_SIZE)
    val iconPosition = getIconPosition(buttonWrapper, renderedIcon)
    val labelIconPosition = getIconPosition(buttonWrapper, EmptyIcon.create(labelIconSize))

    val iconOffset = when (anchorEnum) {
      ToolWindowAnchorEnum.LEFT -> Point(0, scaledInsets.leftRightExtraInset + scaledInsets.iconLabelInset + labelWidth)
      ToolWindowAnchorEnum.RIGHT -> Point(0, scaledInsets.leftRightExtraInset)
      ToolWindowAnchorEnum.TOP,
      ToolWindowAnchorEnum.BOTTOM,
        -> Point(scaledInsets.leftRightExtraInset, 0)
    }
    iconPosition.translate(iconOffset.x, iconOffset.y)
    labelIconPosition.translate(iconOffset.x, iconOffset.y)

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
          val iconCenterX = labelIconPosition.x + labelIconSize / 2
          val baselineX = iconCenterX + (fm.ascent - fm.descent) / 2
          val textBottomY = labelIconPosition.y - scaledInsets.iconLabelInset
          g2.drawString(text, -textBottomY, baselineX)
        }
        ToolWindowAnchorEnum.RIGHT -> {
          g2.rotate(Math.PI / 2)
          val iconCenterX = labelIconPosition.x + labelIconSize / 2
          val baselineX = iconCenterX - (fm.ascent - fm.descent) / 2
          val textTopY = labelIconPosition.y + labelIconSize + scaledInsets.iconLabelInset
          g2.drawString(text, textTopY, -baselineX)
        }
        ToolWindowAnchorEnum.TOP,
        ToolWindowAnchorEnum.BOTTOM,
          -> {
          val textX = labelIconPosition.x + labelIconSize + scaledInsets.iconLabelInset
          val iconCenterY = labelIconPosition.y + labelIconSize / 2
          val baselineY = iconCenterY + (fm.ascent - fm.descent) / 2
          g2.drawString(text, textX, baselineY)
        }
      }
    }
  }

  override fun paintDraggingButton(g: Graphics, toolbarAnchor: ToolWindowAnchorEnum) {
    val color = JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_FLOATING_BACKGROUND
    val iconPadding = getIconPadding(toolbarAnchor)
    val areaSize = button.size.also {
      JBInsets.removeFrom(it, button.insets)
      JBInsets.removeFrom(it, iconPadding)
    }
    val rect = Rectangle(areaSize)
    paintLookBackground(g, rect, color)

    val g2 = g.create() as Graphics2D
    try {
      g2.translate(-(button.insets.left + iconPadding.left), -(button.insets.top + iconPadding.top))
      paintIcon(g2, button, button.icon)
    }
    finally {
      g2.dispose()
    }

    paintLookBorder(g, rect, color)
  }

  override fun getPreferredSize(size: Dimension): Dimension {
    val labelAndExtraSpace = getButtonScaledInsets().fullWidth + getLabelWidth()
    if (toolWindow.anchor.toEnum().isHorizontal()) {
      size.width += labelAndExtraSpace
    }
    else {
      size.height += labelAndExtraSpace
    }
    return size
  }

  private fun getLabelWidth(): Int {
    return UIUtil.computeStringWidth(button, button.getFontMetrics(getTextFont()), getStripeText())
  }

  private fun getTextFont() = if (compactMode) JBFont.create(button.font).lessOn(1f) else button.font

  private fun getStripeText(): String {
    return (toolWindow.stripeShortTitleProvider?.get() ?: toolWindow.stripeTitleProvider.get()).trim()
  }

  private fun getButtonScaledInsets(): ButtonScaledInsets {
    return if (compactMode)
      ButtonScaledInsets(leftRightExtraInset = JBUIScale.scale(6), iconLabelInset = JBUIScale.scale(4))
    else ButtonScaledInsets(leftRightExtraInset = JBUIScale.scale(8), iconLabelInset = JBUIScale.scale(6))
  }

  private data class ButtonScaledInsets(
    val leftRightExtraInset: Int, // For vertical used as top/bottom extra inset
    val iconLabelInset: Int,
  ) {
    val fullWidth: Int
      get() = leftRightExtraInset * 2 + iconLabelInset
  }
}
