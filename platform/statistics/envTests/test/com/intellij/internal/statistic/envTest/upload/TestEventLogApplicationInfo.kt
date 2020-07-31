// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.internal.statistic.eventLog.*
import java.io.File
import java.nio.file.Path

internal const val SETTINGS_ROOT = "settings/%s/%s.json"

internal const val RECORDER_ID = "FUS"
internal const val PRODUCT_VERSION = "2020.1"
internal const val PRODUCT_CODE = "IC"

internal class TestEventLogApplicationInfo(recorderId: String, private val settingsUrl: String)
  : EventLogInternalApplicationInfo(recorderId, false) {
  override fun getProductVersion(): String = PRODUCT_VERSION
  override fun getProductCode(): String = PRODUCT_CODE
  override fun getTemplateUrl(): String = settingsUrl
}

internal class TestEventLogRecorderConfig(recorderId: String, logFiles: List<File>, val sendEnabled: Boolean = true)
  : EventLogInternalRecorderConfig(recorderId) {
  private val evenLogFilesProvider = object : EventLogFilesProvider {
    override fun getLogFilesDir(): Path? = null

    override fun getLogFiles(): List<EventLogFile> = logFiles.map { EventLogFile(it) }
  }

  override fun isSendEnabled(): Boolean = sendEnabled

  override fun getLogFilesProvider(): EventLogFilesProvider = evenLogFilesProvider
}

internal fun newSendService(container: ApacheContainer,
                            logFiles: List<File>,
                            settingsResponseFile: String = SETTINGS_ROOT,
                            sendEnabled: Boolean = true): EventLogStatisticsService {
  val applicationInfo = TestEventLogApplicationInfo(RECORDER_ID, container.getBaseUrl(settingsResponseFile).toString())
  return EventLogStatisticsService(
    DeviceConfiguration(EventLogConfiguration.deviceId, EventLogConfiguration.bucket),
    TestEventLogRecorderConfig(RECORDER_ID, logFiles, sendEnabled),
    EventLogSendListener { _, _, _ -> Unit },
    EventLogUploadSettingsService(RECORDER_ID, applicationInfo)
  )
}