// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_MESSAGE_REQUEST_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_SELECTED_PROVIDER_ID_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.prompt.ui.context.buildExtensionActionDataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptExtensionActionDataContextTest {
  @Test
  fun addsSelectedProviderIdToExtensionActionDataContext() {
    val existingKey = DataKey.create<String>("test.existing")
    val baseDataContext = SimpleDataContext.builder()
      .add(existingKey, "value")
      .build()

    val enrichedDataContext = buildExtensionActionDataContext(baseDataContext, selectedProviderId = "codex")

    assertEquals("value", existingKey.getData(enrichedDataContext))
    assertEquals("codex", AGENT_PROMPT_SELECTED_PROVIDER_ID_DATA_KEY.getData(enrichedDataContext))
  }

  @Test
  fun addsMessageRequestToExtensionActionDataContext() {
    val messageRequest = AgentPromptInitialMessageRequest(
      prompt = "Review the selected changes",
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = "file",
          title = "build.gradle.kts",
          body = "build.gradle.kts",
        )
      ),
    )

    val enrichedDataContext = buildExtensionActionDataContext(
      baseDataContext = SimpleDataContext.builder().build(),
      selectedProviderId = null,
      messageRequest = messageRequest,
    )

    assertEquals(messageRequest, AGENT_PROMPT_MESSAGE_REQUEST_DATA_KEY.getData(enrichedDataContext))
  }

  @Test
  fun leavesDataContextUnchangedWhenExtensionAttributesMissing() {
    val baseDataContext = SimpleDataContext.builder().build()

    val enrichedDataContext = buildExtensionActionDataContext(baseDataContext, selectedProviderId = null)

    assertNull(AGENT_PROMPT_SELECTED_PROVIDER_ID_DATA_KEY.getData(enrichedDataContext))
    assertNull(AGENT_PROMPT_MESSAGE_REQUEST_DATA_KEY.getData(enrichedDataContext))
  }
}
