// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.concurrency.JobScheduler
import com.intellij.internal.statistic.collectors.fus.os.OsVersionUsageCollector
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
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
    FUStateUsagesLogger.logStateEvent(OS_GROUP, getOSName(), getOSVersion())

    val data = FeatureUsageData().addVersion(Version(1, JavaVersion.current().feature, 0))
    FUStateUsagesLogger.logStateEvent(JAVA_GROUP, getJavaVendor(), data)
  }

  private fun getOSVersion(): FeatureUsageData {
    val osData = FeatureUsageData()
    if (SystemInfo.isLinux) {
      val linuxRelease = OsVersionUsageCollector.getLinuxRelease()
      osData.addData("release", linuxRelease.release)
      osData.addVersionByString(linuxRelease.version)
    }
    else {
      osData.addVersion(OsVersionUsageCollector.parse(SystemInfo.OS_VERSION))
    }
    return osData
  }

  private fun getOSName() : String {
    return when {
      SystemInfo.isLinux -> "Linux"
      SystemInfo.isMac -> "Mac"
      SystemInfo.isWindows -> "Windows"
      SystemInfo.isFreeBSD -> "FreeBDS"
      SystemInfo.isSolaris -> "Solaris"
      else -> "Other"
    }
  }

  private fun getJavaVendor() : String {
    return when {
      SystemInfo.isJetBrainsJvm -> "JetBrains"
      SystemInfo.isAppleJvm -> "Apple"
      SystemInfo.isOracleJvm -> "Oracle"
      SystemInfo.isSunJvm -> "Sun"
      SystemInfo.isIbmJvm -> "IBM"
      SystemInfo.isAzulJvm -> "Azul"
      else -> "Other"
    }
  }
}
