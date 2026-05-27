// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

class JunieSessionCostLoaderTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun sumsExactCostAcrossMultipleModelsFromRootEvents() {
    val sessionsRoot = tempDir.resolve(".junie").resolve("sessions")
    writeJunieEvents(
      sessionsRoot = sessionsRoot,
      sessionId = "session-exact",
      sessionA2uxLlmEvent(model = "gpt-4.1-mini-2025-04-14", cost = "0.0001384"),
      sessionA2uxLlmEvent(model = "gemini-3-flash-preview", cost = "0.018754"),
    )

    val cost = JunieSessionCostLoader(sessionsRootPathProvider = { sessionsRoot }).loadCost("session-exact")

    assertThat(cost).isEqualTo(
      AgentSessionCost(
        amountUsd = BigDecimal("0.0188924"),
        kind = AgentSessionCostKind.EXACT,
      ),
    )
  }

  @Test
  fun marksPartialKnownTotalsAsEstimated() {
    val sessionsRoot = tempDir.resolve(".junie").resolve("sessions")
    writeJunieEvents(
      sessionsRoot = sessionsRoot,
      sessionId = "session-partial",
      sessionA2uxLlmEvent(model = "gpt-4.1-mini-2025-04-14", cost = "0.50"),
      sessionA2uxLlmEvent(model = "gpt-4.1-2025-04-14", cost = null),
    )

    val cost = JunieSessionCostLoader(sessionsRootPathProvider = { sessionsRoot }).loadCost("session-partial")

    assertThat(cost).isEqualTo(
      AgentSessionCost(
        amountUsd = BigDecimal("0.50"),
        kind = AgentSessionCostKind.ESTIMATED,
      ),
    )
  }

  @Test
  fun ignoresNestedTaskStorageWhenRootEventsHaveNoCost() {
    val sessionsRoot = tempDir.resolve(".junie").resolve("sessions")
    writeJunieEvents(
      sessionsRoot = sessionsRoot,
      sessionId = "session-root-only",
      sessionA2uxLlmEvent(model = "gpt-4.1-mini-2025-04-14", cost = null),
    )
    val nestedTaskRoot = sessionsRoot.resolve("session-root-only").resolve("task-1").resolve(".matterhorn")
    Files.createDirectories(nestedTaskRoot)
    Files.writeString(
      nestedTaskRoot.resolve("state.json"),
      sessionA2uxLlmEvent(model = "gemini-3-flash-preview", cost = "99.99") + "\n",
    )

    val cost = JunieSessionCostLoader(sessionsRootPathProvider = { sessionsRoot }).loadCost("session-root-only")

    assertThat(cost).isNull()
  }
}

private fun writeJunieEvents(sessionsRoot: Path, sessionId: String, vararg lines: String) {
  val sessionDir = sessionsRoot.resolve(sessionId)
  Files.createDirectories(sessionDir)
  Files.writeString(sessionDir.resolve("events.jsonl"), lines.joinToString(separator = "\n", postfix = "\n"))
}

private fun sessionA2uxLlmEvent(model: String, cost: String?): String {
  val costField = cost?.let { "\"cost\":$it," } ?: ""
  return """
    {"kind":"SessionA2uxEvent","event":{"agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"$model",${costField}"inputTokens":1,"cacheInputTokens":0,"cacheCreateTokens":0,"outputTokens":1}]}}}
  """.trimIndent()
}
