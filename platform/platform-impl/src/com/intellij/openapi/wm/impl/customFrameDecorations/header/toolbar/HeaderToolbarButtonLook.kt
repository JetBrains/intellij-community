// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

private val iconSize: Int
  get() = JBUI.scale(20)

class HeaderToolbarButtonLook : IdeaActionButtonLook() {
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
    val scaledIcon = scaleIcon(icon, iconSize)
    super.paintIcon(g, actionButton, scaledIcon)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon, x: Int, y: Int) {
    val scaledIcon = scaleIcon(icon, iconSize)
    super.paintIcon(g, actionButton, scaledIcon, x, y)
  }

  private fun scaleIcon(icon: Icon, size: Int) : Icon {
    if (icon is ScalableIcon && icon.iconWidth != size) {
      return IconLoader.loadCustomVersionOrScale(icon, size.toFloat());
    }

    return icon;
  }
}