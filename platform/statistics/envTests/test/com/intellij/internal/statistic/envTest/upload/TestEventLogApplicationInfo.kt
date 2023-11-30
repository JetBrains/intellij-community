// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.connection.EventLogSendListener
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsService
import com.intellij.internal.statistic.uploader.EventLogExternalSendConfig
import java.io.File

internal const val SETTINGS_ROOT = "settings/%s/%s.json"

internal const val RECORDER_ID = "FUS"
internal const val PRODUCT_VERSION = "2020.1"
internal const val PRODUCT_CODE = "IC"

internal class TestEventLogApplicationInfo(private val settingsUrl: String): EventLogInternalApplicationInfo(false, false) {
  override fun getProductVersion(): String = PRODUCT_VERSION
  override fun getProductCode(): String = PRODUCT_CODE
  override fun getTemplateUrl(): String = settingsUrl
}

internal class TestEventLogSendConfig(recorderId: String,
                                      deviceId: String,
                                      bucket: Int,
                                      machineId: MachineId,
                                      sendEnabled: Boolean = true,
                                      logFiles: List<File> = emptyList())
  : EventLogExternalSendConfig(recorderId, deviceId, bucket, machineId, logFiles.map { it.absolutePath }, sendEnabled)

internal fun newSendService(container: ApacheContainer,
                            logFiles: List<File>,
                            settingsResponseFile: String = SETTINGS_ROOT,
                            sendEnabled: Boolean = true,
                            machineId: MachineId? = null): EventLogStatisticsService {
  val settingsUrl = container.getBaseUrl(settingsResponseFile).toString()
  val config = EventLogConfiguration.getInstance().getOrCreate(RECORDER_ID)
  return EventLogStatisticsService(
    TestEventLogSendConfig(RECORDER_ID, config.deviceId, config.bucket, machineId ?: config.machineId, sendEnabled, logFiles),
    EventLogSendListener { _, _, _ -> },
    EventLogUploadSettingsService(RECORDER_ID, TestEventLogApplicationInfo(settingsUrl))
  )
}