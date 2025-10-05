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
  private val GROUP = EventLogGroup(ID, 1, "FUS")
  override fun getGroup(): EventLogGroup = GROUP

  val restartField: BooleanEventField = EventFields.Boolean("restart", "Don't start external uploader because there is restarted")
  val runningFromSourcesField: BooleanEventField = EventFields.Boolean("running_from_sources", "Don't start external uploader because IDE is running from sources")
  val sendingOnExitDisabledField: BooleanEventField = EventFields.Boolean("sending_onexit_not_enabled", "Don't start external uploader because sending on exit is disabled")
  val notEnabledLoggerProvidersField: BooleanEventField = EventFields.Boolean("not_enabled_logger_providers", "Don't start external uploader because there are no enabled logger providers")
  val updateInProgressField: BooleanEventField = EventFields.Boolean("update_in_progress", "Don't start external uploader because update is in progress")
  val sendingForAllRecordersDisabledField: BooleanEventField = EventFields.Boolean("sending_disabled_for_all_recorders", "Don't start external uploader because sending logs is disabled for all recorders")
  val failedToStartField: BooleanEventField = EventFields.Boolean("failed_to_start", "Failed to start external log uploader")

  val externalUploaderLaunched: VarargEventId = GROUP.registerVarargEvent("external.uploader.launched",
                                                                          "Send the reason why external log uploader wasn't launched",
                                                                          restartField,
                                                                          runningFromSourcesField,
                                                                          sendingOnExitDisabledField,
                                                                          notEnabledLoggerProvidersField,
                                                                          updateInProgressField,
                                                                          sendingForAllRecordersDisabledField,
                                                                          failedToStartField)
}