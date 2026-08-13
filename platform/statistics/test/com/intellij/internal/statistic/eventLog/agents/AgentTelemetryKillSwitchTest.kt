// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.agents

import com.intellij.internal.statistic.eventLog.RecorderOptionProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The option parsing and the fail-open rule. The application-facing entry point short-circuits to true in unit test
 * mode, so these drive the extracted decision directly, with option values parsed by the real provider.
 */
class AgentTelemetryKillSwitchTest {
  @Test
  fun `an absent option leaves every group reporting`() {
    val options = RecorderOptionProvider(emptyMap())

    assertTrue(isEnabled(options, "agent.workbench"))
    assertTrue(isEnabled(options, "agent.workbench", "tooling.setup.reported"))
  }

  @Test
  fun `a listed group stops reporting, and so does every event in it`() {
    val options = RecorderOptionProvider(
      mapOf(AgentTelemetryKillSwitch.DISABLED_GROUPS_OPTION to "agent.workbench, llm.chat.agents")
    )

    assertFalse(isEnabled(options, "agent.workbench"))
    assertFalse(isEnabled(options, "agent.workbench", "tooling.setup.reported"))
    assertFalse(isEnabled(options, "llm.chat.agents"))
    assertTrue(isEnabled(options, "mcpserver.events"))
  }

  @Test
  fun `a listed event stops reporting while the rest of its group keeps working`() {
    val options = RecorderOptionProvider(
      mapOf(AgentTelemetryKillSwitch.DISABLED_GROUPS_OPTION to "llm.chat.agents/tooling.setup.reported")
    )

    assertFalse(isEnabled(options, "llm.chat.agents", "tooling.setup.reported"))
    assertTrue(isEnabled(options, "llm.chat.agents", "agent.installed"))
    assertTrue(isEnabled(options, "llm.chat.agents"))
  }

  @Test
  fun `an empty option value is not a request to disable everything`() {
    val options = RecorderOptionProvider(mapOf(AgentTelemetryKillSwitch.DISABLED_GROUPS_OPTION to "   "))

    assertTrue(isEnabled(options, "agent.workbench"))
    assertTrue(isEnabled(options, "agent.workbench", "tooling.setup.reported"))
  }

  /** Feeds the real decision the same option value the recorder would hand it. */
  private fun isEnabled(options: RecorderOptionProvider, groupId: String, eventId: String? = null): Boolean =
    AgentTelemetryKillSwitch.isEnabled(
      disabledEntries = options.getListOption(AgentTelemetryKillSwitch.DISABLED_GROUPS_OPTION),
      groupId = groupId,
      eventId = eventId,
    )
}
