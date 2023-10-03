// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

abstract class ChooseProductActionButton(text: @NlsActions.ActionText String? = null, @NlsActions.ActionDescription description: String? = null, icon: Icon? = null)
  : DumbAwareAction(text, description, icon), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = createButton()
    button.isOpaque = false
    button.addActionListener {
      performAction(button, place, presentation)
    }
    button.text = presentation.getText(true)

    return ButtonHolder(button)
  }

  protected fun performAction(component: JComponent, place: String, presentation: Presentation) {
    val dataContext = ActionToolbar.getDataContextFor(component)
    val event = AnActionEvent.createFromInputEvent(null, place, presentation, dataContext)

    if (ActionUtil.lastUpdateAndCheckDumb(this, event, true)) {
      ActionUtil.performActionDumbAwareWithCallbacks(this, event)
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    if(component is ButtonHolder) {
      updateButtonFromPresentation(component.button, presentation)
    }
  }

  protected open fun createButton(): JButton {
    return OnboardingDialogButtons.createButton(false)
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

  class ButtonHolder(val button: JButton) : JPanel(BorderLayout(0, 0)) {
    init {
      isOpaque = false
      add(button)
      border = JBUI.Borders.empty(2, 0)
    }
  }
}