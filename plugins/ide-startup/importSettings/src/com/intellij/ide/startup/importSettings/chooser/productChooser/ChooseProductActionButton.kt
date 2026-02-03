// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.startup.importSettings.chooser.ui.ProductChooserButton
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JBDimension
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

    return wrapButton(button)
  }

  open fun wrapButton(button: JButton): JComponent {
    return ButtonHolder(button)
  }

  private fun performAction(component: JComponent, place: String, presentation: Presentation) {
    val dataContext = ActionToolbar.getDataContextFor(component)
    val event = AnActionEvent.createFromInputEvent(null, place, presentation, dataContext)

    val result = ActionUtil.performAction(this, event)
    if (result.isPerformed) {
      ImportSettingsEventsCollector.productPageDropdownClicked(this)
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val button = when(component) {
      is ButtonHolder -> component.button
      is JButton -> component
      else -> null
    }
    button?.let {
      updateButtonFromPresentation(button, presentation)
    }
  }

  protected open fun createButton(): JButton {
    return ProductChooserButton().getComponent()
  }

  protected open fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    button.isEnabled = presentation.isEnabled
    button.isVisible = presentation.isVisible
    button.text = presentation.getText(true)
    button.icon = presentation.icon
    button.mnemonic = presentation.mnemonic
    button.displayedMnemonicIndex = presentation.displayedMnemonicIndex
    button.toolTipText = presentation.description
    button.putClientProperty(UiUtils.POPUP, presentation.getClientProperty(UiUtils.POPUP))
  }

  class ButtonHolder(val button: JButton) : JPanel(BorderLayout(0, 0)) {
    init {
      val buttonGap = 4

      preferredSize = JBDimension(280, 40+(buttonGap * 2))
      isOpaque = false
      add(button)
      border = JBUI.Borders.empty(buttonGap, 0)
    }
  }
}