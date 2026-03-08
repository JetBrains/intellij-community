// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.ui.components.JBTextArea
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke
import javax.swing.text.DefaultEditorKit

internal fun installPromptEnterHandlers(
  promptArea: JBTextArea,
  canSubmit: () -> Boolean,
  isTabQueueEnabled: () -> Boolean = { false },
  onSubmit: () -> Unit,
  onTabFocusTransfer: () -> Unit = promptArea::transferFocus,
  onTabBackwardFocusTransfer: () -> Unit = promptArea::transferFocusBackward,
) {
  val popupSubmitActionKey = "agent.prompt.submit"
  val popupNewLineActionKey = "agent.prompt.insert.break"
  val popupQueueActionKey = "agent.prompt.queue"
  val popupBackwardFocusActionKey = "agent.prompt.focus.backward"

  promptArea.focusTraversalKeysEnabled = false

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), popupSubmitActionKey)
  promptArea.actionMap.put(popupSubmitActionKey, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      canSubmit()
      onSubmit()
    }
  })

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), popupNewLineActionKey)
  promptArea.actionMap.put(popupNewLineActionKey, promptArea.actionMap.get(DefaultEditorKit.insertBreakAction))

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), popupQueueActionKey)
  promptArea.actionMap.put(popupQueueActionKey, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      if (isTabQueueEnabled()) {
        onSubmit()
      }
      else {
        onTabFocusTransfer()
      }
    }
  })

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), popupBackwardFocusActionKey)
  promptArea.actionMap.put(popupBackwardFocusActionKey, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      onTabBackwardFocusTransfer()
    }
  })
}
