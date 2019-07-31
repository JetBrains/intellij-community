// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.project.Project
import java.util.*

internal enum class ChangeReminderEvent {
  HANDLER_REGISTERED,
  PLUGIN_DISABLED,
  PREDICTION_CALCULATED,
  NOT_SHOWED,
  DIALOG_CLOSED,
  COMMITTED_ANYWAY,
  COMMIT_CANCELED
}

internal enum class ChangeReminderData {
  EXECUTION_TIME,
  SHOW_DIALOG_TIME
}

internal fun <T : Enum<*>> T.getReportedId() = this.name.toLowerCase(Locale.ENGLISH).replace('_', '.')
internal fun <T : Enum<*>> T.getReportedFieldName() = this.name.toLowerCase(Locale.ENGLISH)

internal fun logEvent(project: Project, event: ChangeReminderEvent, factor: ChangeReminderData, value: Long) =
  logEvent(project, event, mapOf(factor to value))

internal fun logEvent(project: Project, event: ChangeReminderEvent, data: Map<ChangeReminderData, Long> = emptyMap()) {
  val logData = FeatureUsageData()

  data.forEach { (factor, value) ->
    logData.addData(factor.getReportedFieldName(), value)
  }

  FUCounterUsageLogger.getInstance().logEvent(project, "vcs.change.reminder", event.getReportedId(), logData)
}