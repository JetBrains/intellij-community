// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.internal.statistic.eventLog.connection.StatisticsResult
import com.intellij.internal.statistic.eventLog.connection.StatisticsService
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import org.jetbrains.annotations.ApiStatus

/**
 * Adapter that lets legacy in-process callers of [com.intellij.internal.statistic.utils.StatisticsUploadAssistant.getEventLogStatisticsService]
 * (notification action, marketplace scheduler, etc.) route a send request through the recorder's
 * [com.jetbrains.fus.reporting.FusClient].
 *
 * The out-of-process uploader (`EventLogUploader`) keeps using [com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService]
 * directly — only the in-IDE sender migrated.
 */
@ApiStatus.Internal
class DispatcherBackedStatisticsService(private val recorderId: String) : StatisticsService {
  override fun send(): StatisticsResult {
    val fusClient = IntellijSensitiveDataValidator.getInstance(recorderId).fusClient
                    ?: return StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "No FUS client for recorder '$recorderId'")
    // flushEvents() flushes the queue and sends; it blocks because the client is built with enableAsyncEventLogging=false.
    fusClient.flushEvents()
    return StatisticsResult(StatisticsResult.ResultCode.SEND, "OK")
  }
}
