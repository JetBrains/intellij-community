// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

class IconLabelButton(icon: Icon, onClickHandler: (JComponent) -> Unit): JBLabel(icon) {
  private var hovered = false
  private val lb = this

  init {
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (SwingUtilities.isLeftMouseButton(e)) {
          onClickHandler(this@IconLabelButton)
        }
      }

      override fun mouseEntered(e: MouseEvent?) {
        hovered = true
        lb.repaint()
      }

      override fun mouseExited(e: MouseEvent?) {
        hovered = false
        lb.repaint()
      }
    })
  }

  override fun paintComponent(g: Graphics?) {
    if (hovered)
      ActionButtonLook.SYSTEM_LOOK.paintBackground(g, lb, ActionButtonComponent.SELECTED)
    super.paintComponent(g)
  }
}