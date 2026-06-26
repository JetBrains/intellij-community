// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.agent.workbench.prompt.ui.emptyState.INLINE_EMPTY_STATE_PROMPT_PROPERTY
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextSink
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
internal class AgentWorkbenchGlobalPromptEmptyTextProviderTest {
  @Test
  fun providesGlobalPromptHintWhenInlineDisabled() {
    withInlineEmptyStatePrompt(enabled = false) {
      val sink = RecordingSink()

      AgentWorkbenchGlobalPromptEmptyTextProvider().appendEmptyText(JPanel(), sink)

      assertThat(sink.actions).containsExactly(
        RecordedAction(
          actionId = AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID,
          text = AgentPromptBundle.message("action.AgentWorkbenchPrompt.OpenGlobalPalette.text"),
        )
      )
    }
  }

  @Test
  fun suppressesHintWhenInlineEnabled() {
    withInlineEmptyStatePrompt(enabled = true) {
      val sink = RecordingSink()

      AgentWorkbenchGlobalPromptEmptyTextProvider().appendEmptyText(JPanel(), sink)

      assertThat(sink.actions).isEmpty()
    }
  }

  private fun withInlineEmptyStatePrompt(enabled: Boolean, body: () -> Unit) {
    val previous = System.getProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY)
    System.setProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY, enabled.toString())
    try {
      body()
    }
    finally {
      if (previous == null) System.clearProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY)
      else System.setProperty(INLINE_EMPTY_STATE_PROMPT_PROPERTY, previous)
    }
  }

  private class RecordingSink : EditorEmptyTextSink {
    val actions = mutableListOf<RecordedAction>()

    override fun appendLine(line: String) {
    }

    override fun appendAction(action: String, shortcut: String?) {
    }

    override fun appendActionWithShortcuts(action: String, actionId: String) {
      actions.add(RecordedAction(actionId = actionId, text = action))
    }

    override fun appendActionWithFirstKeyboardShortcut(action: String, actionId: String) {
    }

    override fun appendToolWindow(action: String, toolWindowId: String) {
    }
  }

  private data class RecordedAction(
    val actionId: String,
    val text: String,
  )
}
