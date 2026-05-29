// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.cost

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenRouterModelMatcherTest {
  @Test
  fun matchesByNormalizedTailAndRejectsAmbiguousPrefixes() {
    val snapshot = OpenRouterPriceSnapshot(
      fetchedAt = 1L,
      entries = listOf(
        openRouterPriceEntry(
          id = "anthropic/claude-opus-4.7-fast",
          canonicalSlug = "anthropic/claude-4.7-opus-fast-20260512",
          displayName = "Anthropic: Claude Opus 4.7 (Fast)",
        ),
        openRouterPriceEntry(
          id = "openai/gpt-chat-latest",
          canonicalSlug = "openai/gpt-chat-latest-20260505",
          displayName = "OpenAI: GPT Chat Latest",
        ),
        openRouterPriceEntry(
          id = "openai/gpt-4.1-mini",
          canonicalSlug = "openai/gpt-4.1-mini-20260501",
          displayName = "OpenAI: GPT-4.1 Mini",
        ),
      ),
    )

    assertThat(matchOpenRouterModel("claude-opus-4.7-fast-20260512", snapshot)?.id)
      .isEqualTo("anthropic/claude-opus-4.7-fast")
    assertThat(matchOpenRouterModel("gpt", snapshot)).isNull()
  }

  @Test
  fun matchesUnambiguousPrefixFallback() {
    val snapshot = OpenRouterPriceSnapshot(
      fetchedAt = 1L,
      entries = listOf(
        openRouterPriceEntry(
          id = "openai/gpt-chat-latest",
          canonicalSlug = "openai/gpt-chat-latest-20260505",
          displayName = "OpenAI: GPT Chat Latest",
        ),
      ),
    )

    assertThat(matchOpenRouterModel("gpt-chat", snapshot)?.id)
      .isEqualTo("openai/gpt-chat-latest")
  }
}

private fun openRouterPriceEntry(
  id: String,
  canonicalSlug: String,
  displayName: String,
): OpenRouterPriceEntry {
  return OpenRouterPriceEntry(
    id = id,
    canonicalSlug = canonicalSlug,
    displayName = displayName,
    normalizedNames = setOf(
      canonicalSlug.replace('/', '-').replace('.', '-'),
      canonicalSlug.substringAfter('/').replace('.', '-'),
      id.replace('/', '-').replace('.', '-'),
      id.substringAfter('/').replace('.', '-'),
      displayName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
    ),
    promptTokenPriceUsd = null,
    completionTokenPriceUsd = null,
    cacheReadTokenPriceUsd = null,
    cacheWriteTokenPriceUsd = null,
  )
}
