// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.impl.headertoolbar.adjustIconForHeader
import com.intellij.openapi.wm.impl.headertoolbar.isDarkHeader
import com.intellij.ui.JBColor
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.RgbImageFilterSupplier
import com.intellij.ui.icons.getDisabledIcon
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.ui.GrayFilter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

@ApiStatus.Internal
val lightThemeDarkHeaderDisableFilter: RgbImageFilterSupplier = object : RgbImageFilterSupplier {
  private val filter = GrayFilter(0, 0, 30)

  override fun getFilter() = filter
}

fun getHeaderBackgroundColor(component: JComponent, state: Int): Color? {
  if (ProjectWindowCustomizerService.getInstance().isActive()) {
    return when (state) {
      ActionButtonComponent.NORMAL -> if (component.isBackgroundSet) component.background else null
      else -> JBColor.namedColor("MainToolbar.Dropdown.transparentHoverBackground", UIManager.getColor("MainToolbar.Icon.hoverBackground"))
    }
  }
  return when (state) {
    ActionButtonComponent.NORMAL -> if (component.isBackgroundSet) component.background else null
    ActionButtonComponent.PUSHED -> UIManager.getColor("MainToolbar.Icon.pressedBackground")
                                    ?: UIManager.getColor("ActionButton.pressedBackground")
    else -> UIManager.getColor("MainToolbar.Icon.hoverBackground")
            ?: UIManager.getColor("ActionButton.hoverBackground")
  }
}

open class HeaderToolbarButtonLook(
  private val iconSize: () -> Int = { JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSize() }
) : IdeaActionButtonLook() {
  override fun getButtonArc(): JBValue {
    return JBUI.CurrentTheme.MainToolbar.Button.hoverArc()
  }

  override fun getStateBackground(component: JComponent, state: Int): Color? = getHeaderBackgroundColor(component, state)

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}

  override fun getDisabledIcon(icon: Icon): Icon {
    return getDisabledIcon(icon = icon, disableFilter = lightThemeDarkHeaderDisableFilter)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val scaledIcon = scaleAndAdjustIcon(icon)
    val iconPosition = getIconPosition(actionButton, scaledIcon)
    paintIconImpl(g, actionButton, scaledIcon, iconPosition.x, iconPosition.y)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon, x: Int, y: Int) {
    val scaledIcon = scaleAndAdjustIcon(icon)
    val originalSize = Dimension(icon.iconWidth, icon.iconHeight)
    val scaledSize = Dimension(scaledIcon.iconWidth, scaledIcon.iconHeight)
    val scaledX = x - (scaledSize.width - originalSize.width) / 2
    val scaledY = y - (scaledSize.height - originalSize.height) / 2

    paintIconImpl(g, actionButton, scaledIcon, scaledX, scaledY)
  }

  override fun paintDownArrow(g: Graphics?, actionButton: ActionButtonComponent?, originalIcon: Icon, arrowIcon: Icon) {
    val scaledOriginalIcon = scaleAndAdjustIcon(originalIcon)
    val scaledArrowIcon = scaleAndAdjustIcon(arrowIcon)
    super.paintDownArrow(g, actionButton, scaledOriginalIcon, scaledArrowIcon)
  }

  private fun scaleAndAdjustIcon(icon: Icon): Icon {
    val iconSize = iconSize()
    if (icon is CachedImageIcon) {
      return loadIconCustomVersionOrScale(icon = icon, size = iconSize, isDark = isDarkHeader().takeIf { it })
    }
    else {
      return adjustIconForHeader(if (icon is ScalableIcon) loadIconCustomVersionOrScale(icon = icon, size = iconSize) else icon)
    }
  }
}