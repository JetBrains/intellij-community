// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions

import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenCodeAcpGenerationModelCatalogTest {
  @Test
  fun parsesAcpModelsAndVariantOptions() {
    val models = parseOpenCodeAcpGenerationModels(
      """
      {
        "id": 2,
        "jsonrpc": "2.0",
        "result": {
          "models": {
            "currentModelId": "anthropic/claude-sonnet-4",
            "availableModels": [
              {"modelId": "anthropic/claude-sonnet-4", "name": "Claude Sonnet 4"},
              {"modelId": "openai/gpt-5"}
            ]
          },
          "configOptions": [
            {
              "id": "variant",
              "options": [
                {"name": "Low", "value": "low"},
                {"name": "High", "value": "high"},
                {"name": "Max", "value": "max"}
              ]
            }
          ]
        }
      }
      """.trimIndent(),
    )

    assertThat(models).hasSize(2)
    assertThat(models[0].id).isEqualTo("anthropic/claude-sonnet-4")
    assertThat(models[0].displayName).isEqualTo("Claude Sonnet 4")
    assertThat(models[0].isDefault).isTrue()
    assertThat(models[0].supportedReasoningEfforts).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.MAX,
    )
    assertThat(models[1].displayName).isEqualTo("openai/gpt-5")
  }

  @Test
  fun fallsBackToModelConfigOption() {
    val models = parseOpenCodeAcpGenerationModels(
      """
      {
        "id": 2,
        "jsonrpc": "2.0",
        "result": {
          "configOptions": [
            {
              "id": "model",
              "currentValue": "openai/gpt-5",
              "options": [{"name": "GPT-5", "value": "openai/gpt-5"}]
            },
            {
              "category": "reasoning_effort",
              "options": [{"value": "minimal"}, {"value": "medium"}]
            }
          ]
        }
      }
      """.trimIndent(),
    )

    assertThat(models).hasSize(1)
    val model = models.single()
    assertThat(model.id).isEqualTo("openai/gpt-5")
    assertThat(model.displayName).isEqualTo("GPT-5")
    assertThat(model.isDefault).isTrue()
    assertThat(model.supportedReasoningEfforts).containsExactly(AgentPromptReasoningEffort.LOW, AgentPromptReasoningEffort.MEDIUM)
  }

  @Test
  fun usesDefaultEffortsWhenAcpDoesNotExposeVariantOptions() {
    val models = parseOpenCodeAcpGenerationModels(
      """
      {
        "id": 2,
        "jsonrpc": "2.0",
        "result": {
          "configOptions": [
            {
              "id": "model",
              "currentValue": "opencode/big-pickle",
              "options": [{"name": "Big Pickle", "value": "opencode/big-pickle"}]
            }
          ]
        }
      }
      """.trimIndent(),
    )

    assertThat(models.single().supportedReasoningEfforts).containsExactly(
      AgentPromptReasoningEffort.LOW,
      AgentPromptReasoningEffort.MEDIUM,
      AgentPromptReasoningEffort.HIGH,
      AgentPromptReasoningEffort.MAX,
    )
  }
}
