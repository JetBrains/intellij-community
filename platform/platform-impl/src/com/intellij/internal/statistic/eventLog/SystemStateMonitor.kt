// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.concurrency.JobScheduler
import com.intellij.internal.statistic.collectors.fus.os.OsVersionUsageCollector
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.lang.JavaVersion
import java.util.concurrent.TimeUnit

class SystemStateMonitor : FeatureUsageStateEventTracker {
  private val OS_GROUP = FeatureUsageGroup("system.os", 1)
  private val JAVA_GROUP = FeatureUsageGroup("system.java", 1)

  private val INITIAL_DELAY = 0
  private val PERIOD_DELAY = 24 * 60

  override fun initialize() {
    if (!FeatureUsageLogger.isEnabled()) {
      return
    }

    JobScheduler.getScheduler().scheduleWithFixedDelay(
      { logSystemEvent() },
      INITIAL_DELAY.toLong(), PERIOD_DELAY.toLong(), TimeUnit.MINUTES
    )
  }

  private fun logSystemEvent() {
    FUStateUsagesLogger.logStateEvent(OS_GROUP, getOSName(), FeatureUsageData().addData("version", getOSVersion()))

    val data = FeatureUsageData().addData("version", "1." + JavaVersion.current().feature)
    FUStateUsagesLogger.logStateEvent(JAVA_GROUP, System.getProperty("java.vendor", "Unknown"), data)
  }

  private fun getOSName() : String {
    return when {
      SystemInfo.isLinux -> "Linux"
      SystemInfo.isMac -> "Mac.OS.X"
      SystemInfo.isWindows -> "Windows"
      else -> SystemInfo.OS_NAME
    }
  }

  private fun getOSVersion() : String {
    if (SystemInfo.isLinux) {
      return OsVersionUsageCollector.getLinuxOSVersion()
    }
    return OsVersionUsageCollector.parseVersion(SystemInfo.OS_VERSION)
  }
}
