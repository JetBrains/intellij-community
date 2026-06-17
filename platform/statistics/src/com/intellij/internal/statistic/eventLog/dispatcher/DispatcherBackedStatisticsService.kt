// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.internal.statistic.eventLog.connection.StatisticsResult
import com.intellij.internal.statistic.eventLog.connection.StatisticsService
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus

/**
 * Adapter that lets legacy in-process callers of [com.intellij.internal.statistic.utils.StatisticsUploadAssistant.getEventLogStatisticsService]
 * (notification action, marketplace scheduler, etc.) route a send request through [IntellijReportDispatcher].
 *
 * The out-of-process uploader (`EventLogUploader`) keeps using [com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService]
 * directly — only the in-IDE sender migrated.
 */
@ApiStatus.Internal
class DispatcherBackedStatisticsService(private val recorderId: String) : StatisticsService {
  override fun send(): StatisticsResult {
    val dispatcher = IntellijSensitiveDataValidator.getInstance(recorderId).reportDispatcher
                     ?: return StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "No report dispatcher for recorder '$recorderId'")
    val sent = runBlocking { dispatcher.send() }
    return if (sent) StatisticsResult(StatisticsResult.ResultCode.SEND, "OK")
    else StatisticsResult(StatisticsResult.ResultCode.NOTHING_TO_SEND, "Send disabled or queue empty")
  }
}
