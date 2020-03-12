// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ComponentUtil
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

abstract class JButtonAction(text: String?, description: String? = null, icon: Icon? = null)
  : DumbAwareAction(text, description, icon), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = createButton().apply {
      isFocusable = false
    }.also { button ->
      button.addActionListener {
        val toolbar = ComponentUtil.getParentOfType(ActionToolbar::class.java, button)
        val dataContext = toolbar?.toolbarDataContext ?: DataManager.getInstance().getDataContext(button)
        val action = this@JButtonAction
        val event = AnActionEvent.createFromInputEvent(null, place, presentation, dataContext)

        if (ActionUtil.lastUpdateAndCheckDumb(action, event, true)) {
          ActionUtil.performActionDumbAware(action, event)
        }
      }
    }

    val buttonInsets = button.insets
    val leftRight = JBUI.scale(if (SystemInfo.isWindows) 4 else 6) - (buttonInsets.left + buttonInsets.right) / 2
    val topBottom = JBUI.scale(4) - (buttonInsets.top + buttonInsets.bottom) / 2
    val border = JBEmptyBorder(JBInsets.create(topBottom.coerceAtLeast(0), leftRight.coerceAtLeast(0)))
    updateButtonFromPresentation(button, presentation)
    return JBUI.Panels.simplePanel(button).withBorder(border)
  }

  protected open fun createButton(): JButton = JButton()

  protected fun updateButtonFromPresentation(e: AnActionEvent) {
    val button = UIUtil.findComponentOfType(e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY), JButton::class.java)
    if (button != null) updateButtonFromPresentation(button, e.presentation)
  }

  protected open fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    button.isEnabled = presentation.isEnabled
    button.isVisible = presentation.isVisible
    button.text = presentation.text
    button.icon = presentation.icon
    button.mnemonic = presentation.mnemonic
    button.displayedMnemonicIndex = presentation.displayedMnemonicIndex
    button.toolTipText = presentation.description
  }
}