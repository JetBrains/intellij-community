// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager

class IconLabelButton(icon: Icon, private val onClickHandler: (JComponent) -> Unit): JBLabel(icon) {
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

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleIconLabelButton()
    }
    return accessibleContext
  }

  private inner class AccessibleIconLabelButton : AccessibleJLabel(), AccessibleAction {
    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PUSH_BUTTON

    override fun getAccessibleName(): @Nls String? {
      return super.getAccessibleName()?.takeIf(String::isNotEmpty)
             ?: getTooltipAccessibleText()
    }

    override fun getAccessibleDescription(): @Nls String? {
      return getTooltipAccessibleText().takeIf { it != accessibleName }
    }

    override fun getAccessibleAction(): AccessibleAction = this

    override fun getAccessibleActionCount(): Int = 1

    override fun getAccessibleActionDescription(i: Int): String? =
      if (i == 0) UIManager.getString("AbstractButton.clickText") else null

    override fun doAccessibleAction(i: Int): Boolean {
      if (i != 0 || !isEnabled) {
        return false
      }
      onClickHandler(this@IconLabelButton)
      return true
    }

    @Suppress("HardCodedStringLiteral")
    private fun getTooltipAccessibleText(): @Nls String? {
      val toolTipText = this@IconLabelButton.toolTipText ?: return null
      return StringUtil.removeHtmlTags(toolTipText).trim().ifBlank { null }
    }
  }
}