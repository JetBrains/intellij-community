// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.jetbrains.fus.reporting.ReportValidator
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IntellijReportValidator(private val recorderId: String) : ReportValidator<LogEvent> {
  override fun validateEvent(event: LogEvent): LogEvent? = IntellijSensitiveDataValidator.getInstance(recorderId).validateEvent(event)

  override fun isUnreachable(): Boolean = IntellijSensitiveDataValidator.getInstance(recorderId).validationRulesStorage.isUnreachable()
}
