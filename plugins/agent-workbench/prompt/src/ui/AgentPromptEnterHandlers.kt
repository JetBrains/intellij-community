// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.EditorTextField
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal fun installPromptEnterHandlers(
  promptArea: EditorTextField,
  canSubmit: () -> Boolean,
  isTabQueueEnabled: () -> Boolean = { false },
  onSubmit: () -> Unit,
  onTabFocusTransfer: () -> Unit = promptArea::transferFocus,
  onTabBackwardFocusTransfer: () -> Unit = promptArea::transferFocusBackward,
) {
  promptArea.focusTraversalKeysEnabled = false

  promptArea.addSettingsProvider { editor ->
    DumbAwareAction.create {
      canSubmit()
      onSubmit()
    }.registerCustomShortcutSet(
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)),
      editor.contentComponent,
    )

    DumbAwareAction.create {
      val caretOffset = editor.caretModel.offset
      CommandProcessor.getInstance().executeCommand(editor.project, {
        WriteAction.run<RuntimeException> {
          promptArea.document.insertString(caretOffset, "\n")
        }
        editor.caretModel.moveToOffset(caretOffset + 1)
      }, null, null)
    }.registerCustomShortcutSet(
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
      editor.contentComponent,
    )

    DumbAwareAction.create {
      if (isTabQueueEnabled()) {
        onSubmit()
      }
      else {
        onTabFocusTransfer()
      }
    }.registerCustomShortcutSet(
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)),
      editor.contentComponent,
    )

    DumbAwareAction.create {
      onTabBackwardFocusTransfer()
    }.registerCustomShortcutSet(
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)),
      editor.contentComponent,
    )
  }
}
