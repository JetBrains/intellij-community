// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsActions.ActionText
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

abstract class JButtonAction(text: @ActionText String?, @ActionDescription description: String? = null, icon: Icon? = null)
  : DumbAwareAction(text, description, icon), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = createButton()
    button.addActionListener {
      performAction(button, place, presentation)
    }

    return button
  }

  protected fun performAction(component: JComponent, place: String, presentation: Presentation) {
    val dataContext = ActionToolbar.getDataContextFor(component)
    val event = AnActionEvent.createFromInputEvent(null, place, presentation, dataContext)

    if (ActionUtil.lastUpdateAndCheckDumb(this, event, true)) {
      ActionUtil.performActionDumbAwareWithCallbacks(this, event)
    }
  }

  protected open fun createButton(): JButton = JButton().configureForToolbar()

  protected fun JButton.configureForToolbar(): JButton =
    apply {
      isFocusable = false
      font = JBUI.Fonts.toolbarFont()
      putClientProperty("ActionToolbar.smallVariant", true)
    }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    if (component is JButton) {
      updateButtonFromPresentation(component, presentation)
    }
  }

  protected open fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    button.isEnabled = presentation.isEnabled
    button.isVisible = presentation.isVisible
    button.text = presentation.getText(true)
    button.icon = presentation.icon
    button.mnemonic = presentation.mnemonic
    button.displayedMnemonicIndex = presentation.displayedMnemonicIndex
    button.toolTipText = presentation.description
  }
}