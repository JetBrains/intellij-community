// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.ClientProperty
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

@TestApplication
class AgentPromptEnterHandlersTest {
  @Test
  fun enterSubmitsWhenNewTaskCanSubmit() {
    runInEdtAndWait {
      var submitCalls = 0
      withEditorTextField({ promptArea ->
        installPromptEnterHandlers(
          promptArea = promptArea,
          canSubmit = { true },
          onSubmit = { submitCalls++ },
        )
      }) { promptArea ->
        invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
        assertThat(submitCalls).isEqualTo(1)
      }
    }
  }

  @Test
  fun enterSubmitsWhenExistingTaskCannotSubmit() {
    runInEdtAndWait {
      var submitCalls = 0
      withEditorTextField({ promptArea ->
        installPromptEnterHandlers(
          promptArea = promptArea,
          canSubmit = { false },
          onSubmit = { submitCalls++ },
        )
      }) { promptArea ->
        invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
        assertThat(submitCalls).isEqualTo(1)
      }
    }
  }

  @Test
  fun enterSubmitsWhenNewTaskCannotSubmit() {
    runInEdtAndWait {
      var submitCalls = 0
      withEditorTextField({ promptArea ->
        installPromptEnterHandlers(
          promptArea = promptArea,
          canSubmit = { false },
          onSubmit = { submitCalls++ },
        )
      }) { promptArea ->
        invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
        assertThat(submitCalls).isEqualTo(1)
      }
    }
  }

  @Test
  fun shiftEnterInsertsLineBreakWithoutSubmitCallbacks() {
    runInEdtAndWait {
      var submitCalls = 0
      withEditorTextField({ promptArea ->
        installPromptEnterHandlers(
          promptArea = promptArea,
          canSubmit = { true },
          onSubmit = { submitCalls++ },
        )
      }) { promptArea ->
        promptArea.text = "prompt"
        promptArea.editor!!.caretModel.moveToOffset(promptArea.text.length)
        invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))
        assertThat(promptArea.text).isEqualTo("prompt\n")
        assertThat(submitCalls).isZero()
      }
    }
  }

  @Test
  fun tabSubmitsWhenTabQueueShortcutEnabled() {
    runInEdtAndWait {
      var submitCalls = 0
      var forwardFocusCalls = 0
      withEditorTextField({ promptArea ->
        installPromptEnterHandlers(
          promptArea = promptArea,
          canSubmit = { false },
          isTabQueueEnabled = { true },
          onSubmit = { submitCalls++ },
          onTabFocusTransfer = { forwardFocusCalls++ },
        )
      }) { promptArea ->
        invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0))
        assertThat(submitCalls).isEqualTo(1)
        assertThat(forwardFocusCalls).isZero()
      }
    }
  }

  @Test
  fun tabSelectsNextPromptTabWhenTabQueueShortcutDisabled() {
    runInEdtAndWait {
      var submitCalls = 0
      var forwardFocusCalls = 0
      withEditorTextField({ promptArea ->
        installPromptEnterHandlers(
          promptArea = promptArea,
          canSubmit = { true },
          isTabQueueEnabled = { false },
          onSubmit = { submitCalls++ },
          onTabFocusTransfer = { forwardFocusCalls++ },
        )
      }) { promptArea ->
        invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0))
        assertThat(submitCalls).isZero()
        assertThat(forwardFocusCalls).isEqualTo(1)
      }
    }
  }

  @Test
  fun shiftTabSelectsPreviousPromptTabWithoutSubmitCallbacks() {
    runInEdtAndWait {
      var submitCalls = 0
      var backwardFocusCalls = 0
      withEditorTextField({ promptArea ->
        installPromptEnterHandlers(
          promptArea = promptArea,
          canSubmit = { true },
          isTabQueueEnabled = { true },
          onSubmit = { submitCalls++ },
          onTabBackwardFocusTransfer = { backwardFocusCalls++ },
        )
      }) { promptArea ->
        invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK))
        assertThat(submitCalls).isZero()
        assertThat(backwardFocusCalls).isEqualTo(1)
      }
    }
  }

  @Test
  fun existingTaskSelectorEnterInvokesConfirmAction() {
    runInEdtAndWait {
      val list = JBList(arrayOf("thread-1"))
      var confirmCalls = 0

      installConfirmActionOnEnter(list) {
        confirmCalls++
        true
      }

      invokeComponentKeyAction(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))

      assertThat(confirmCalls).isEqualTo(1)
    }
  }

  @Test
  fun confirmActionOnEnterDelegatesToFallbackWhenConfirmationIsNotHandled() {
    runInEdtAndWait {
      val component = JPanel()
      val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
      var fallbackCalls = 0
      var confirmCalls = 0

      component.registerKeyboardAction({ fallbackCalls++ }, enter, JComponent.WHEN_FOCUSED)
      installConfirmActionOnEnter(component) {
        confirmCalls++
        false
      }

      invokeComponentKeyAction(component, enter)

      assertThat(confirmCalls).isEqualTo(1)
      assertThat(fallbackCalls).isEqualTo(1)
    }
  }

  @Test
  fun confirmActionOnEnterSkipsFallbackWhenConfirmationIsHandled() {
    runInEdtAndWait {
      val component = JPanel()
      val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
      var fallbackCalls = 0
      var confirmCalls = 0

      component.registerKeyboardAction({ fallbackCalls++ }, enter, JComponent.WHEN_FOCUSED)
      installConfirmActionOnEnter(component) {
        confirmCalls++
        true
      }

      invokeComponentKeyAction(component, enter)

      assertThat(confirmCalls).isEqualTo(1)
      assertThat(fallbackCalls).isZero()
    }
  }

  @Test
  fun selectAdjacentPromptTabWrapsForwardToFirstTab() {
    runInEdtAndWait {
      val tabbedPane = JBTabbedPane().apply {
        addTab("New", JPanel())
        addTab("Existing", JPanel())
        addTab("Extension", JPanel())
        selectedIndex = 2
      }

      selectAdjacentPromptTab(tabbedPane, 1)

      assertThat(tabbedPane.selectedIndex).isZero()
    }
  }

  @Test
  fun selectAdjacentPromptTabWrapsBackwardToLastTab() {
    runInEdtAndWait {
      val tabbedPane = JBTabbedPane().apply {
        addTab("New", JPanel())
        addTab("Existing", JPanel())
        addTab("Extension", JPanel())
        selectedIndex = 0
      }

      selectAdjacentPromptTab(tabbedPane, -1)

      assertThat(tabbedPane.selectedIndex).isEqualTo(2)
    }
  }

  /**
   * Creates an [EditorTextField], calls [configure] to register settings providers,
   * then initializes the editor, runs [block], and cleans up the editor instance.
   */
  private inline fun withEditorTextField(
    configure: (EditorTextField) -> Unit,
    block: (EditorTextField) -> Unit,
  ) {
    val field = EditorTextField()
    configure(field)
    field.addNotify()
    try {
      block(field)
    }
    finally {
      field.removeNotify()
    }
  }

  private fun invokeKeyAction(promptArea: EditorTextField, keyStroke: KeyStroke) {
    val matchingAction = findMatchingAction(promptArea, keyStroke)
    checkNotNull(matchingAction) { "No action registered for keystroke: $keyStroke" }
    val dataContext = SimpleDataContext.builder().build()
    val event = AnActionEvent.createEvent(matchingAction, dataContext, null, "", ActionUiKind.NONE, null)
    matchingAction.actionPerformed(event)
  }

  private fun findMatchingAction(promptArea: EditorTextField, keyStroke: KeyStroke): AnAction? {
    val editor = checkNotNull(promptArea.editor) { "Editor was not initialized" }
    val contentComponent = editor.contentComponent
    val actions: List<AnAction> = ClientProperty.get(contentComponent, AnAction.ACTIONS_KEY) ?: emptyList()
    val targetShortcut = KeyboardShortcut(keyStroke, null)
    return actions.firstOrNull { action ->
      action.shortcutSet.shortcuts.any { shortcut -> shortcut == targetShortcut }
    }
  }

  private fun invokeComponentKeyAction(component: JComponent, keyStroke: KeyStroke) {
    val action = checkNotNull(component.getActionForKeyStroke(keyStroke)) { "No action registered for keystroke: $keyStroke" }
    action.actionPerformed(ActionEvent(component, ActionEvent.ACTION_PERFORMED, keyStroke.toString()))
  }
}
