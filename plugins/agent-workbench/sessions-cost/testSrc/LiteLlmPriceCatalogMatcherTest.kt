// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.cost

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LiteLlmPriceCatalogMatcherTest {
  @Test
  fun matchesExistingAliasVariantsAgainstLiteLlmModels() {
    val snapshot = LiteLlmPriceCatalog.parseCatalog(
      """
        {
          "claude-sonnet-4-6": {
            "input_cost_per_token": 0.000003,
            "output_cost_per_token": 0.000015,
            "cache_creation_input_token_cost": 0.00000375,
            "cache_read_input_token_cost": 0.0000003
          },
          "gpt-5.1-codex-mini": {
            "input_cost_per_token": 0.00000025,
            "output_cost_per_token": 0.000002,
            "cache_read_input_token_cost": 0.000000025
          }
        }
      """.trimIndent(),
    )

    assertThat(LiteLlmPriceCatalog.matchEntry("anthropic/claude-4.6-sonnet-20260217", snapshot)?.displayName)
      .isEqualTo("Anthropic: Claude Sonnet 4.6")
    assertThat(LiteLlmPriceCatalog.matchEntry("openai/gpt-5.1-codex-mini-20251113", snapshot)?.displayName)
      .isEqualTo("OpenAI: GPT-5.1 Codex Mini")
  }

  @Test
  fun matchesDirectModelsAndRejectsAmbiguousPrefixes() {
    val snapshot = LiteLlmPriceCatalog.parseCatalog(
      """
        {
          "gpt-5.4": {
            "input_cost_per_token": 0.0000025,
            "output_cost_per_token": 0.000015
          },
          "gpt-5.4-mini": {
            "input_cost_per_token": 0.00000025,
            "output_cost_per_token": 0.000002
          }
        }
      """.trimIndent(),
    )

    assertThat(LiteLlmPriceCatalog.matchEntry("gpt-5.4", snapshot)?.displayName).isEqualTo("OpenAI: GPT-5.4")
    assertThat(LiteLlmPriceCatalog.matchEntry("gpt-5", snapshot)).isNull()
  }
}
