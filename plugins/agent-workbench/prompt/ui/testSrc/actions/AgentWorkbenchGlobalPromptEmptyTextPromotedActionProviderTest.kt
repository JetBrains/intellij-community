// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPromotedActionProvider
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
internal class AgentWorkbenchGlobalPromptEmptyTextPromotedActionProviderTest {
  @Test
  fun providesGlobalPromptPromotion() {
    val promotedAction = AgentWorkbenchGlobalPromptEmptyTextPromotedActionProvider().getPromotedAction(JPanel())

    assertThat(promotedAction).isEqualTo(
      EditorEmptyTextPromotedActionProvider.PromotedAction(
        actionId = AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID,
        text = AgentPromptBundle.message("action.AgentWorkbenchPrompt.OpenGlobalPalette.text"),
      )
    )
  }
}
