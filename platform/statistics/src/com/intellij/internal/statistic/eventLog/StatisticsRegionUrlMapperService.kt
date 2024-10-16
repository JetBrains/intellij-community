// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus.Internal

/**
Service to access [com.intellij.ide.RegionUrlMapper] from *intellij.platform.ide.impl* module without introducing dependency to this module
 */
@Internal
abstract class StatisticsRegionUrlMapperService {
  companion object {
    private val fallBackInstance: StatisticsRegionUrlMapperService by lazy { StatisticsRegionUrlMapperServiceFallBack() }

    fun getInstance(): StatisticsRegionUrlMapperService {
      val service = try {
        ApplicationManager.getApplication().getService(StatisticsRegionUrlMapperService::class.java)
      }
      catch (_: Exception) {
        null
      }
      // Explicitly accept only exactly known implementation. Can't use sealed class as implementation is in another module.
      return if ("com.intellij.internal.statistic.StatisticsRegionUrlMapperServiceImpl" == service?.javaClass?.name) service else fallBackInstance
    }
  }

  abstract fun getRegionUrl(): String?

  /**
  Fall back implementation in case there is a problem with required service implementation
   */
  private class StatisticsRegionUrlMapperServiceFallBack : StatisticsRegionUrlMapperService() {
    override fun getRegionUrl(): String = EventLogInternalApplicationInfo.EVENT_LOG_SETTINGS_URL_TEMPLATE
  }
}