// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.jetbrains.fus.reporting.ReportValidator
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IntellijReportValidator(private val recorderId: String) : ReportValidator<LogEvent> {
  /**
   * The validated event never carries the JCP payload. The payload stays in the queued event for the raw data of a
   * listener, and the record and the upload take the validated event only.
   */
  override fun validateEvent(event: LogEvent): LogEvent? =
    IntellijSensitiveDataValidator.getInstance(recorderId).validateEvent(event)?.also { it.event.data.remove(FeatureUsageData.JCP_DATA_KEY) }

  override fun isUnreachable(): Boolean = IntellijSensitiveDataValidator.getInstance(recorderId).validationRulesStorage.isUnreachable()
}
