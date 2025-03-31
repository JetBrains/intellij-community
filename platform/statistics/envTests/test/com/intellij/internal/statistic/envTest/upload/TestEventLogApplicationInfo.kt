// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.internal.statistic.eventLog.MachineId
import com.intellij.internal.statistic.eventLog.connection.EventLogSendListener
import com.intellij.internal.statistic.eventLog.connection.EventLogSettingsClient
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService
import com.intellij.internal.statistic.uploader.EventLogExternalSendConfig
import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import java.io.File
import java.util.concurrent.TimeUnit

internal const val RECORDER_ID = "FUS"
internal const val PRODUCT_VERSION = "2020.1"
internal const val PRODUCT_CODE = "IC"
internal const val METADATA_VERSION = 201
internal const val SETTINGS_ROOT = "settings/$RECORDER_ID/$PRODUCT_CODE.json"

internal class TestEventLogApplicationInfo() : EventLogInternalApplicationInfo(false, false) {
  override fun getProductVersion(): String = PRODUCT_VERSION
  override fun getProductCode(): String = PRODUCT_CODE
  override fun getBaselineVersion(): Int = METADATA_VERSION
}

internal class TestEventLogUploadSettingsClient(
  configUrl: String,
  configCacheTimeoutMs: Long = TimeUnit.MINUTES.toMillis(10),
) : EventLogSettingsClient() {
  override val applicationInfo = TestEventLogApplicationInfo()
  override val configurationClient = ConfigurationClientFactory.createTest(
    applicationInfo.productCode,
    applicationInfo.productVersion,
    applicationInfo.connectionSettings,
    configCacheTimeoutMs,
    configUrl
  )
  override val recorderId: String = RECORDER_ID
}

internal class TestEventLogSendConfig(
  recorderId: String,
  deviceId: String,
  bucket: Int,
  machineId: MachineId,
  sendEnabled: Boolean = true,
  logFiles: List<File> = emptyList()
) : EventLogExternalSendConfig(recorderId, deviceId, bucket, machineId, logFiles.map { it.absolutePath }, sendEnabled)

internal fun newSendService(
  container: ApacheContainer,
  logFiles: List<File>,
  settingsResponseFile: String = SETTINGS_ROOT,
  sendEnabled: Boolean = true,
  machineId: MachineId? = null,
): EventLogStatisticsService {
  val settingsUrl = container.getBaseUrl(settingsResponseFile).toString()
  val config = EventLogConfiguration.getInstance().getOrCreate(RECORDER_ID)
  return EventLogStatisticsService(
    TestEventLogSendConfig(RECORDER_ID, config.deviceId, config.bucket, machineId ?: config.machineId, sendEnabled, logFiles),
    EventLogSendListener { _, _, _ -> },
    TestEventLogUploadSettingsClient(settingsUrl)
  )
}