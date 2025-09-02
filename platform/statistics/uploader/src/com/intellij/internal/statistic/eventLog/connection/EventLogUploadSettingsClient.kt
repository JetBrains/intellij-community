// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection

import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo
import com.jetbrains.fus.reporting.configuration.ConfigurationClient
import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import com.jetbrains.fus.reporting.configuration.RegionCode
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit

/**
 * The Client periodically downloads configuration.
 * The frequency can be set, it is 10 minutes by default.
 * Use China configuration url for the China region.
 * Use the test configuration url if applicationInfo.isTestConfig is true.
 * */
@ApiStatus.Internal
open class EventLogUploadSettingsClient(
  override val recorderId: String,
  override val applicationInfo: EventLogApplicationInfo,
  cacheTimeoutMs: Long = TimeUnit.MINUTES.toMillis(10)
) : EventLogSettingsClient() {
  companion object {
    @VisibleForTesting
    val chinaRegion: String = "china" //com.intellij.ide.Region.CHINA
  }
  override var configurationClient: ConfigurationClient = ConfigurationClientFactory.create(recorderId,
                                                                                            applicationInfo.productCode,
                                                                                            applicationInfo.productVersion,
                                                                                            applicationInfo.isTestConfig,
                                                                                            applicationInfo.connectionSettings,
                                                                                            if (applicationInfo.regionalCode == chinaRegion)
                                                                                             RegionCode.CN else RegionCode.ALL,
                                                                                            cacheTimeoutMs)
}
