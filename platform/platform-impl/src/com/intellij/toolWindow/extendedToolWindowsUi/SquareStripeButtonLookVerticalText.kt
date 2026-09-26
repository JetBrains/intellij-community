// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.extendedToolWindowsUi

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLookExtension
import com.intellij.openapi.wm.impl.ToolWindowAnchorEnum
import com.intellij.openapi.wm.impl.isHorizontal
import com.intellij.openapi.wm.impl.toEnum
import com.intellij.toolWindow.StripeButtonUi
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.UIManager

internal class SquareStripeButtonLookVerticalText(button: SquareStripeButton) : SquareStripeButtonLookExtension(button) {

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
    val labelIconSize = JBUIScale.scale(ToolWindowStripeExtension.ICON_UNSCALED_SIZE)
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
    val verticalOffset = JBUIScale.scale(if (UISettings.getInstance().compactMode) 1 else 2)
    val leftStripeVerticalOffset = JBUIScale.scale(1)

    UIUtil.useSafely(g!!) { g2 ->
      g2.color = getForegroundColor()
      g2.font = f
      UISettings.setupAntialiasing(g2)

      when (anchorEnum) {
        ToolWindowAnchorEnum.LEFT -> {
          g2.rotate(-Math.PI / 2)
          val iconCenterX = labelIconPosition.x + labelIconSize / 2
          val baselineX = iconCenterX + (fm.ascent - fm.descent) / 2 + verticalOffset
          val textBottomY = labelIconPosition.y - scaledInsets.iconLabelInset
          g2.drawString(text, -textBottomY, baselineX)
        }
        ToolWindowAnchorEnum.RIGHT -> {
          g2.rotate(Math.PI / 2)
          val iconCenterX = labelIconPosition.x + labelIconSize / 2
          val baselineX = iconCenterX - (fm.ascent - fm.descent) / 2 - leftStripeVerticalOffset
          val textTopY = labelIconPosition.y + labelIconSize + scaledInsets.iconLabelInset
          g2.drawString(text, textTopY, -baselineX)
        }
        ToolWindowAnchorEnum.TOP,
        ToolWindowAnchorEnum.BOTTOM,
          -> {
          val textX = labelIconPosition.x + labelIconSize + scaledInsets.iconLabelInset
          val iconCenterY = labelIconPosition.y + labelIconSize / 2
          val baselineY = iconCenterY + (fm.ascent - fm.descent) / 2 + verticalOffset
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

  private fun getTextFont() = if (UISettings.getInstance().compactMode) JBFont.create(button.font).lessOn(1f) else button.font

  private fun getStripeText(): String {
    // Don't use short title the plugin
    return toolWindow.stripeTitleProvider.get().trim()
  }

  private fun getButtonScaledInsets(): ButtonScaledInsets {
    return if (UISettings.getInstance().compactMode)
      ButtonScaledInsets(leftRightExtraInset = JBUIScale.scale(6), iconLabelInset = JBUIScale.scale(4))
    else ButtonScaledInsets(leftRightExtraInset = JBUIScale.scale(4), iconLabelInset = JBUIScale.scale(6))
  }

  private data class ButtonScaledInsets(
    val leftRightExtraInset: Int, // For vertical used as top/bottom extra inset
    val iconLabelInset: Int,
  ) {
    val fullWidth: Int
      get() = leftRightExtraInset * 2 + iconLabelInset
  }
}
