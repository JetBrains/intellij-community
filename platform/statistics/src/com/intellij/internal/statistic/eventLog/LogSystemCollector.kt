// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

/**
 * The system collector records internal system logs.
 */
@ApiStatus.Internal
object LogSystemCollector : CounterUsagesCollector() {
  private const val ID = "system.log"
  private val GROUP = EventLogGroup(ID, 2)
  override fun getGroup(): EventLogGroup = GROUP

  val restartField: BooleanEventField = EventFields.Boolean("restart", "If true, the external uploader wasn't started because IDE was restarted")
  val runningFromSourcesField: BooleanEventField = EventFields.Boolean("running_from_sources", "If true, the external uploader wasn't started because IDE was running from sources")
  val sendingOnExitDisabledField: BooleanEventField = EventFields.Boolean("sending_onexit_not_enabled", "If true, the external uploader wasn't started because sending on exit is disabled")
  val notEnabledLoggerProvidersField: BooleanEventField = EventFields.Boolean("not_enabled_logger_providers", "If true, the external uploader wasn't started because there are no enabled logger providers")
  val updateInProgressField: BooleanEventField = EventFields.Boolean("update_in_progress", "If true, the external uploader wasn't started because an update is in progress")
  val sendingForAllRecordersDisabledField: BooleanEventField = EventFields.Boolean("sending_disabled_for_all_recorders", "If true, the external uploader wasn't started because sending logs is disabled for all recorders")
  val failedToStartField: BooleanEventField = EventFields.Boolean("failed_to_start", "External log uploader failed to start")

  val externalUploaderLaunched: VarargEventId = GROUP.registerVarargEvent(
    "external.uploader.launched",
    restartField, runningFromSourcesField, sendingOnExitDisabledField, notEnabledLoggerProvidersField, updateInProgressField,
    sendingForAllRecordersDisabledField, failedToStartField
  )
}
