// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.impl.headertoolbar.isDarkHeader
import com.intellij.util.ui.JBValue
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

private const val iconSize = 20

internal class HeaderToolbarButtonLook : IdeaActionButtonLook() {

  override fun getStateBackground(component: JComponent, state: Int): Color = when (state) {
    ActionButtonComponent.NORMAL -> component.background
    ActionButtonComponent.PUSHED -> UIManager.getColor("MainToolbar.Icon.pressedBackground")
                                    ?: UIManager.getColor("ActionButton.pressedBackground")
    else -> UIManager.getColor("MainToolbar.Icon.hoverBackground")
            ?: UIManager.getColor("ActionButton.hoverBackground")
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}
  override fun getButtonArc(): JBValue = JBValue.Float(0f)

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val scaledIcon = scaleIcon(adjustColor(icon))
    super.paintIcon(g, actionButton, scaledIcon)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon, x: Int, y: Int) {
    val scaledIcon = scaleIcon(adjustColor(icon))
    super.paintIcon(g, actionButton, scaledIcon, x, y)
  }

  override fun paintDownArrow(g: Graphics?, actionButton: ActionButtonComponent?, originalIcon: Icon, arrowIcon: Icon) {
    val scaledOriginalIcon = scaleIcon(adjustColor(originalIcon))
    val scaledArrowIcon = scaleIcon(adjustColor(arrowIcon))
    super.paintDownArrow(g, actionButton, scaledOriginalIcon, scaledArrowIcon)
  }

  private fun adjustColor(icon: Icon) =
    if (isDarkHeader()) IconLoader.getDarkIcon(icon, true) else icon

  private fun scaleIcon(icon: Icon) : Icon {
    if (icon is ScalableIcon) {
      return IconLoader.loadCustomVersionOrScale(icon, iconSize)
    }

    return icon
  }
}