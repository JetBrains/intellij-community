// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBTextArea
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@TestApplication
class AgentPromptEnterHandlersTest {
  @Test
  fun enterSubmitsWhenNewTaskCanSubmit() {
    runInEdtAndWait {
      val promptArea = JBTextArea()
      var submitCalls = 0
      var existingDisabledCalls = 0

      installPromptEnterHandlers(
        promptArea = promptArea,
        canSubmit = { true },
        targetMode = { PromptTargetMode.NEW_TASK },
        onSubmit = { submitCalls++ },
        onExistingTaskSubmitDisabled = { existingDisabledCalls++ },
      )

      invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))

      assertThat(submitCalls).isEqualTo(1)
      assertThat(existingDisabledCalls).isZero()
    }
  }

  @Test
  fun enterShowsExistingDisabledWhenExistingTaskCannotSubmit() {
    runInEdtAndWait {
      val promptArea = JBTextArea()
      var submitCalls = 0
      var existingDisabledCalls = 0

      installPromptEnterHandlers(
        promptArea = promptArea,
        canSubmit = { false },
        targetMode = { PromptTargetMode.EXISTING_TASK },
        onSubmit = { submitCalls++ },
        onExistingTaskSubmitDisabled = { existingDisabledCalls++ },
      )

      invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))

      assertThat(submitCalls).isZero()
      assertThat(existingDisabledCalls).isEqualTo(1)
    }
  }

  @Test
  fun enterDoesNothingWhenNewTaskCannotSubmit() {
    runInEdtAndWait {
      val promptArea = JBTextArea()
      var submitCalls = 0
      var existingDisabledCalls = 0

      installPromptEnterHandlers(
        promptArea = promptArea,
        canSubmit = { false },
        targetMode = { PromptTargetMode.NEW_TASK },
        onSubmit = { submitCalls++ },
        onExistingTaskSubmitDisabled = { existingDisabledCalls++ },
      )

      invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))

      assertThat(submitCalls).isZero()
      assertThat(existingDisabledCalls).isZero()
    }
  }

  @Test
  fun shiftEnterInsertsLineBreakWithoutSubmitCallbacks() {
    runInEdtAndWait {
      val promptArea = JBTextArea().apply {
        text = "prompt"
        caretPosition = text.length
      }
      var submitCalls = 0
      var existingDisabledCalls = 0

      installPromptEnterHandlers(
        promptArea = promptArea,
        canSubmit = { true },
        targetMode = { PromptTargetMode.NEW_TASK },
        onSubmit = { submitCalls++ },
        onExistingTaskSubmitDisabled = { existingDisabledCalls++ },
      )

      invokeKeyAction(promptArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))

      assertThat(promptArea.text).isEqualTo("prompt\n")
      assertThat(submitCalls).isZero()
      assertThat(existingDisabledCalls).isZero()
    }
  }

  private fun invokeKeyAction(promptArea: JBTextArea, keyStroke: KeyStroke) {
    val actionKey = checkNotNull(promptArea.inputMap.get(keyStroke)) {
      "Action key was not registered for keystroke: $keyStroke"
    }
    val action = checkNotNull(promptArea.actionMap.get(actionKey)) {
      "Action was not registered in actionMap for key: $actionKey"
    }
    action.actionPerformed(ActionEvent(promptArea, ActionEvent.ACTION_PERFORMED, "test"))
  }
}
