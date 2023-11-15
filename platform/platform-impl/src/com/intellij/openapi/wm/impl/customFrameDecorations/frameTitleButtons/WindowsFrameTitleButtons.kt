// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons

import com.intellij.icons.AllIcons
import com.intellij.openapi.wm.impl.customFrameDecorations.TitleButtonsPanel
import com.intellij.openapi.wm.impl.customFrameDecorations.style.HOVER_KEY
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.accessibility.AccessibleContext
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonUI

class WindowsFrameTitleButtons(
  myCloseAction: Action,
  myRestoreAction: Action? = null,
  myIconifyAction: Action? = null,
  myMaximizeAction: Action? = null) : FrameTitleButtons {
  override val closeButton: JButton = createButton("Close", myCloseAction)
  override val restoreButton: JButton? = myRestoreAction?.let { createButton("Restore", it) }
  override val maximizeButton: JButton? = myMaximizeAction?.let { createButton("Maximize", it) }
  override val minimizeButton: JButton? = myIconifyAction?.let { createButton("Iconify", it) }

  override val restoreIcon = AllIcons.Windows.Restore
  override val restoreInactiveIcon = AllIcons.Windows.RestoreInactive

  override val maximizeIcon = AllIcons.Windows.Maximize
  override val maximizeInactiveIcon = AllIcons.Windows.MaximizeInactive

  override val minimizeIcon = AllIcons.Windows.Minimize
  override val minimizeInactiveIcon = AllIcons.Windows.MinimizeInactive


  override val closeIcon = AllIcons.Windows.CloseActive
  override val closeInactiveIcon = AllIcons.Windows.CloseInactive
  override val closeHoverIcon = IconUtil.colorizeReplace(AllIcons.Windows.CloseActive, JBColor(0xffffff, 0xffffff))

  override fun createButton(accessibleName: String, action: Action): JButton {
    val button = object : JButton() {
      init {
        super.setUI(if (accessibleName == "Close") HoveredButtonUI(Color(0xe81123)) else HoveredButtonUI())
      }

      override fun setUI(ui: ButtonUI?) {
      }
    }
    button.action = action
    button.isFocusable = false
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)
    button.text = null
    return button
  }

  override fun fillButtonPane(panel: TitleButtonsPanel) {
    this.minimizeButton?.let { panel.addComponent(it) }
    this.maximizeButton?.let { panel.addComponent(it) }
    this.restoreButton?.let { panel.addComponent(it) }
    panel.addComponent(this.closeButton)
  }

  override fun setScaledPreferredSize(size: Dimension): Dimension {
    return Dimension(size.width, size.height)
  }
}


private class HoveredButtonUI(private val customBackgroundColor: Color? = null) : BasicButtonUI() {
  override fun paint(g: Graphics, c: JComponent) {
    getHoverColor(c)?.let {
      g.color = customBackgroundColor ?: it
      g.fillRect(0, 0, c.width, c.height)
    }
    super.paint(g, c)
  }

  private fun getHoverColor(c: JComponent): Color? = c.getClientProperty(HOVER_KEY) as? Color
}