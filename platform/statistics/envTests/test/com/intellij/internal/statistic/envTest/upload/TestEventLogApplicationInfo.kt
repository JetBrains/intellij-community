// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.connection.EventLogSendListener
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsService
import java.io.File

internal const val SETTINGS_ROOT = "settings/%s/%s.json"

internal const val RECORDER_ID = "FUS"
internal const val PRODUCT_VERSION = "2020.1"
internal const val PRODUCT_CODE = "IC"

internal class TestEventLogApplicationInfo(recorderId: String)
  : EventLogInternalApplicationInfo(recorderId, false) {
  override fun getProductVersion(): String = PRODUCT_VERSION
  override fun getProductCode(): String = PRODUCT_CODE
  @Deprecated(
    replaceWith = ReplaceWith("EventLogInternalRecorderConfig.getTemplateUrl()"),
    message = "Get template url from recorder configuration, because it depends on the recorder code"
  )
  override fun getTemplateUrl(): String = ""
}

internal class TestEventLogRecorderConfig(recorderId: String,
                                          private val settingsUrl: String,
                                          private val sendEnabled: Boolean = true,
                                          logFiles: List<File> = emptyList())
  : EventLogInternalRecorderConfig(recorderId) {
  private val evenLogFilesProvider = object : FilesToSendProvider {
    override fun getFilesToSend(): List<EventLogFile> = logFiles.map { EventLogFile(it) }
  }

  override fun isSendEnabled(): Boolean = sendEnabled

  override fun getFilesToSendProvider(): FilesToSendProvider = evenLogFilesProvider

  override fun getTemplateUrl(): String = settingsUrl
}

internal fun newSendService(container: ApacheContainer,
                            logFiles: List<File>,
                            settingsResponseFile: String = SETTINGS_ROOT,
                            sendEnabled: Boolean = true,
                            machineId: MachineId? = null): EventLogStatisticsService {
  val settingsUrl = container.getBaseUrl(settingsResponseFile).toString()
  val config = EventLogConfiguration.getInstance().getOrCreate(RECORDER_ID)
  val recorderConfig = TestEventLogRecorderConfig(RECORDER_ID, settingsUrl, sendEnabled, logFiles)
  return EventLogStatisticsService(
    DeviceConfiguration(config.deviceId, config.bucket, machineId ?: config.machineId),
    recorderConfig,
    EventLogSendListener { _, _, _ -> },
    EventLogUploadSettingsService(recorderConfig, TestEventLogApplicationInfo(RECORDER_ID))
  )
}