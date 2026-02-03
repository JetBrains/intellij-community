// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo.EVENT_LOG_SETTINGS_REGION_CODE
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus.Internal

/**
Service to access [com.intellij.ide.RegionSettings.getRegion] from *intellij.platform.ide.impl* module without introducing dependency to this module
 */
@Internal
abstract class StatisticsRegionSettingsService {
  companion object {
    private val fallBackInstance: StatisticsRegionSettingsService by lazy { StatisticsRegionSettingsServiceFallBack() }

    fun getInstance(): StatisticsRegionSettingsService {
      val service = try {
        ApplicationManager.getApplication().getService(StatisticsRegionSettingsService::class.java)
      }
      catch (_: Exception) {
        null
      }
      // Explicitly accept only exactly known implementation. Can't use sealed class as implementation is in another module.
      return if ("com.intellij.internal.statistic.StatisticsRegionSettingsServiceImpl" == service?.javaClass?.name) service else fallBackInstance
    }
  }
  abstract fun getRegionCode(): String?

  /**
  Fallback implementation in case there is a problem with the required service implementation
   */
  private class StatisticsRegionSettingsServiceFallBack : StatisticsRegionSettingsService() {
    override fun getRegionCode(): String = EVENT_LOG_SETTINGS_REGION_CODE
  }
}
