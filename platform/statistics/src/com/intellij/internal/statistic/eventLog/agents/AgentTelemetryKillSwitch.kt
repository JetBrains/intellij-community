// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.agents

import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

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

  /** Recorders whose read failure has already been reported, so a permanent failure does not report once per event. */
  private val reportedReadFailures = ConcurrentHashMap.newKeySet<String>()

  /**
   * Whether [groupId], or [eventId] within it, may report.
   *
   * Fails closed: if the option cannot be read the group stays silent, because a switch that cannot be read cannot be
   * honoured, and the reason to reach for this switch is that a group is doing damage. The read failure is reported as
   * an error rather than swallowed at debug level — losing a group to it is a fault worth seeing — and reported once
   * per recorder, so a permanently unavailable recorder does not raise one report per event.
   *
   * Unit test mode short-circuits to enabled before any recorder lookup: a test that collects these events has no
   * recorder options to read, and a fail-closed answer there would silence every collector test.
   */
  @JvmOverloads
  fun isEnabled(groupId: String, recorderId: String, eventId: String? = null): Boolean {
    if (ApplicationManager.getApplication()?.isUnitTestMode == true) {
      return true
    }
    return isEnabled(groupId, recorderId, eventId) {
      StatisticsEventLogProviderUtil.getEventLogProvider(recorderId).recorderOptionsProvider
        .getListOption(DISABLED_GROUPS_OPTION)
    }
  }

  /**
   * The same decision with the option read supplied, so that the fail-closed rule is testable without a recorder:
   * the entry point above short-circuits in unit test mode and never reaches its own read.
   */
  @VisibleForTesting
  fun isEnabled(groupId: String, recorderId: String, eventId: String?, readDisabledEntries: () -> List<String>?): Boolean {
    val disabled = try {
      readDisabledEntries()
    }
    catch (e: Exception) {
      reportReadFailure(recorderId, groupId, e)
      return false
    }
    return isEnabled(disabled, groupId, eventId)
  }

  private fun reportReadFailure(recorderId: String, groupId: String, e: Exception) {
    val message = "Cannot read $DISABLED_GROUPS_OPTION for recorder $recorderId, so $groupId stays silent"
    if (reportedReadFailures.add(recorderId)) {
      LOG.error(message, e)
    }
    else {
      LOG.debug(message, e)
    }
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
