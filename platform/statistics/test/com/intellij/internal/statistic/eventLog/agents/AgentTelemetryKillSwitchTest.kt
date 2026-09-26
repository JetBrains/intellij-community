// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.agents

import com.intellij.internal.statistic.eventLog.RecorderOptionProvider
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The option parsing and the fail-closed rule. The application-facing entry point short-circuits to true in unit test
 * mode, so these drive the overload that takes the option read, with option values parsed by the real provider.
 */
@TestApplication
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

  @Test
  fun `an option that cannot be read silences the group, and says so as an error`() {
    var enabled = true
    val reportedErrors = mutableListOf<String>()
    LoggedErrorProcessor.executeWith<Throwable>(
      object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
          reportedErrors += message
          return Action.NONE
        }
      }
    ) {
      // A recorder of its own: the failure is reported once per recorder, and another test must not consume it.
      enabled = AgentTelemetryKillSwitch.isEnabled(
        groupId = "agent.workbench",
        recorderId = "UNREADABLE_RECORDER",
        eventId = "tooling.setup.reported",
      ) { throw IllegalStateException("no recorder options here") }
    }

    assertFalse(enabled, "A switch that cannot be read cannot be honoured, so the group has to stay silent")
    assertTrue(
      reportedErrors.singleOrNull()?.contains("agent.workbench") == true,
      "The failure has to be reported as an error naming the group it silenced, or the silence is unattributable: " +
      "$reportedErrors",
    )
  }

  /** Feeds the real decision the same option value the recorder would hand it, through the production read path. */
  private fun isEnabled(options: RecorderOptionProvider, groupId: String, eventId: String? = null): Boolean =
    AgentTelemetryKillSwitch.isEnabled(
      groupId = groupId,
      recorderId = "FUS",
      eventId = eventId,
    ) { options.getListOption(AgentTelemetryKillSwitch.DISABLED_GROUPS_OPTION) }
}
