// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.impl.headertoolbar.adjustIconForHeader
import com.intellij.ui.JBColor
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.ui.GrayFilter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.image.RGBImageFilter
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

@ApiStatus.Internal
val lightThemeDarkHeaderDisableFilter: () -> RGBImageFilter = { GrayFilter(0, 0, 30) }

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

internal class HeaderToolbarButtonLook(
  private val iconSize: () -> Int = { JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSize() }
) : IdeaActionButtonLook() {
  override fun getButtonArc(): JBValue {
    return JBUI.CurrentTheme.MainToolbar.Button.hoverArc()
  }

  override fun getStateBackground(component: JComponent, state: Int): Color? = getHeaderBackgroundColor(component, state)

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}

  override fun getDisabledIcon(icon: Icon): Icon {
    return IconLoader.getDisabledIcon(icon, lightThemeDarkHeaderDisableFilter)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val scaledIcon = scaleIcon(adjustIconForHeader(icon))
    val iconPos = getIconPosition(actionButton, scaledIcon)
    paintIconImpl(g, actionButton, scaledIcon, iconPos.x, iconPos.y)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon, x: Int, y: Int) {
    val scaledIcon = scaleIcon(adjustIconForHeader(icon))
    val originalSize = Dimension(icon.iconWidth, icon.iconHeight)
    val scaledSize = Dimension(scaledIcon.iconWidth, scaledIcon.iconHeight)
    val scaledX = x - (scaledSize.width - originalSize.width) / 2
    val scaledY = y - (scaledSize.height - originalSize.height) / 2

    paintIconImpl(g, actionButton, scaledIcon, scaledX, scaledY)
  }

  override fun paintDownArrow(g: Graphics?, actionButton: ActionButtonComponent?, originalIcon: Icon, arrowIcon: Icon) {
    val scaledOriginalIcon = scaleIcon(adjustIconForHeader(originalIcon))
    val scaledArrowIcon = scaleIcon(adjustIconForHeader(arrowIcon))
    super.paintDownArrow(g, actionButton, scaledOriginalIcon, scaledArrowIcon)
  }

  private fun scaleIcon(icon: Icon) : Icon {
    if (icon is ScalableIcon) {
      return loadIconCustomVersionOrScale(icon = icon, size = iconSize())
    }

    return icon
  }
}