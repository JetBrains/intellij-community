// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.border.Border
import javax.swing.border.CompoundBorder

class ProductChooserButton {
  private val button = OnboardingDialogButtons.createButton(false)

  fun getComponent(): JButton = button

  init {
    button.border = CompoundBorder(ProductChooserBorder(), button.border)
  }
}


class ProductChooserBorder : Border {
  private val icon = AllIcons.General.LinkDropTriangle
  private val iconGap = JBUI.scale(2)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if(c !is JComponent) return
    if(c.getClientProperty(UiUtils.POPUP) != true) return

    val g2 = g.create() as? Graphics2D ?: return
    try {
      val psw = c.preferredSize.width
      val w = (width - psw) / 2

      icon.paintIcon(c, g2, x + psw+ w - icon.iconWidth + iconGap, (height - icon.iconHeight) / 2)
    } finally {
      g2.dispose()
    }
  }


  override fun getBorderInsets(c: Component): Insets {
    return JBUI.emptyInsets()
  }


  override fun isBorderOpaque(): Boolean {
    return false
  }

}

