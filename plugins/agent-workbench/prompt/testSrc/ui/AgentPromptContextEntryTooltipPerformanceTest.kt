// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptChipRender
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptChipRenderInput
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRenderers
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptEnvelopeRenderInput
import com.intellij.agent.workbench.sessions.core.prompt.InMemoryAgentPromptContextRendererRegistry
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptContextEntryTooltipPerformanceTest {
  @Test
  fun tooltipFallbackRendersEnvelopeLazilyAndCachesResult() {
    val renderer = CountingRendererBridge()
    AgentPromptContextRenderers.withRegistryForTest(InMemoryAgentPromptContextRendererRegistry(listOf(renderer))) {
      val entry = ContextEntry(
        item = AgentPromptContextItem(
          rendererId = renderer.rendererId,
          title = "Context",
          body = "body",
          source = "test",
        ),
        projectBasePath = "/work/project",
      )

      assertThat(renderer.chipRenderCount).isEqualTo(1)
      assertThat(renderer.envelopeRenderCount).isZero()

      assertThat(entry.tooltipText).isEqualTo("envelope: body")
      assertThat(renderer.envelopeRenderCount).isEqualTo(1)

      assertThat(entry.tooltipText).isEqualTo("envelope: body")
      assertThat(renderer.envelopeRenderCount).isEqualTo(1)
    }
  }

  @Test
  fun explicitChipTooltipSkipsEnvelopeRendering() {
    val renderer = ExplicitTooltipRendererBridge()
    AgentPromptContextRenderers.withRegistryForTest(InMemoryAgentPromptContextRendererRegistry(listOf(renderer))) {
      val entry = ContextEntry(
        item = AgentPromptContextItem(
          rendererId = renderer.rendererId,
          title = "Context",
          body = "body",
          source = "test",
        ),
      )

      assertThat(renderer.chipRenderCount).isEqualTo(1)
      assertThat(renderer.envelopeRenderCount).isZero()

      assertThat(entry.tooltipText).isEqualTo("chip-tooltip")
      assertThat(entry.tooltipText).isEqualTo("chip-tooltip")
      assertThat(renderer.envelopeRenderCount).isZero()
    }
  }
}

private class CountingRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String = "counting-renderer"

  var chipRenderCount: Int = 0
  var envelopeRenderCount: Int = 0

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    envelopeRenderCount += 1
    return "envelope: ${input.item.body}"
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    chipRenderCount += 1
    return AgentPromptChipRender(text = "chip")
  }
}

private class ExplicitTooltipRendererBridge : AgentPromptContextRendererBridge {
  override val rendererId: String = "explicit-tooltip-renderer"

  var chipRenderCount: Int = 0
  var envelopeRenderCount: Int = 0

  override fun renderEnvelope(input: AgentPromptEnvelopeRenderInput): String {
    envelopeRenderCount += 1
    return "unused"
  }

  override fun renderChip(input: AgentPromptChipRenderInput): AgentPromptChipRender {
    chipRenderCount += 1
    return AgentPromptChipRender(text = "chip", tooltipText = "chip-tooltip")
  }
}
