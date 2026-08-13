// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.agents

import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.annotations.ApiStatus

/**
 * Turns an agent telemetry group off from the server, without waiting for a release.
 *
 * The channel is the recorder option `agent_telemetry_disabled_groups`, a comma-separated list of group ids, read
 * through the same [com.intellij.internal.statistic.eventLog.RecorderOptionProvider] as the TRACE redaction regexes.
 * A registry key was the alternative and was rejected: changing one needs a build.
 *
 * An entry is either a group id, which retires the whole group, or `groupId/eventId`, which retires one event. Events
 * are addressable because most groups here are existing ones that gained an event: turning the group off to stop one
 * new event would take working telemetry down with it.
 */
@ApiStatus.Internal
object AgentTelemetryKillSwitch {
  const val DISABLED_GROUPS_OPTION: String = "agent_telemetry_disabled_groups"

  private val LOG = logger<AgentTelemetryKillSwitch>()

  /**
   * Whether [groupId], or [eventId] within it, may report. Fails open: an unavailable recorder or an unreadable
   * option leaves reporting on, because losing telemetry to a lookup failure is worse than honouring a switch late.
   */
  @JvmOverloads
  fun isEnabled(groupId: String, recorderId: String, eventId: String? = null): Boolean {
    val application = ApplicationManager.getApplication() ?: return true
    if (application.isUnitTestMode) {
      return true
    }
    val disabled = try {
      StatisticsEventLogProviderUtil.getEventLogProvider(recorderId).recorderOptionsProvider
        .getListOption(DISABLED_GROUPS_OPTION)
    }
    catch (e: Exception) {
      LOG.debug("Cannot read $DISABLED_GROUPS_OPTION for recorder $recorderId", e)
      return true
    }
    return isEnabled(disabled, groupId, eventId)
  }

  /** The decision itself, separated from reading the option so that it is testable without an application. */
  @VisibleForTesting
  fun isEnabled(disabledEntries: List<String>?, groupId: String, eventId: String?): Boolean {
    if (disabledEntries.isNullOrEmpty()) {
      return true
    }
    return groupId !in disabledEntries && (eventId == null || "$groupId/$eventId" !in disabledEntries)
  }
}
