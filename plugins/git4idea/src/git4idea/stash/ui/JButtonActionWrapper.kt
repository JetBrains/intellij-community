// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JButtonAction
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke

internal abstract class JButtonActionWrapper(text: @NlsActions.ActionText String, private val isDefault: Boolean = false) :
  JButtonAction(text), ActionWithDelegate<AnAction> {

  override fun createButton(): JButton = object : JButton() {
    override fun isDefaultButton(): Boolean = isDefault
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = super.createCustomComponent(presentation, place)
    button.addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) {
      performAction(button, place, presentation)
    }
    return button
  }

  override fun update(e: AnActionEvent) {
    delegate.update(e)
  }

  override fun actionPerformed(e: AnActionEvent) = delegate.actionPerformed(e)
}