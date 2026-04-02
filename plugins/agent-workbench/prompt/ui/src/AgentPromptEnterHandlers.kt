// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.EditorTextField
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JTabbedPane
import javax.swing.KeyStroke

internal fun selectAdjacentPromptTab(tabbedPane: JTabbedPane, direction: Int) {
  val offset = when {
    direction > 0 -> 1
    direction < 0 -> -1
    else -> return
  }
  val tabCount = tabbedPane.tabCount
  if (tabCount <= 1) return

  val currentIndex = tabbedPane.selectedIndex.takeIf { it >= 0 } ?: 0
  tabbedPane.selectedIndex = (currentIndex + offset + tabCount) % tabCount
}

internal fun installConfirmActionOnEnter(
  component: JComponent,
  onConfirm: () -> Boolean,
) {
  val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
  val fallbackAction = component.getActionForKeyStroke(enter)
  component.registerKeyboardAction({ event: ActionEvent ->
    val handled = onConfirm()
    if (!handled) {
      fallbackAction?.actionPerformed(event)
    }
  }, enter, JComponent.WHEN_FOCUSED)
}

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
      if (executeLookupActionIfActive(editor.contentComponent, editor, IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)) {
        return@create
      }
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
      if (executeLookupActionIfActive(editor.contentComponent, editor, IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE)) {
        return@create
      }
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

private fun executeLookupActionIfActive(component: JComponent, editor: com.intellij.openapi.editor.Editor, actionId: String): Boolean {
  if (LookupManager.getActiveLookup(editor) == null) {
    return false
  }
  val handler = EditorActionManager.getInstance().getActionHandler(actionId)
  handler.execute(editor, editor.caretModel.currentCaret, DataManager.getInstance().getDataContext(component))
  return true
}
