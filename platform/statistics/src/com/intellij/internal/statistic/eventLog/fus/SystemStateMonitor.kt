// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.concurrency.JobScheduler
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.collectors.fus.os.OsVersionUsageCollector
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.util.lang.JavaVersion
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class SystemStateMonitor : FeatureUsageStateEventTracker {
  private val OS_GROUP = EventLogGroup("system.os", 3)
  private val INITIAL_DELAY = 0
  private val PERIOD_DELAY = 24 * 60

  override fun initialize() {
    if (!FeatureUsageLogger.isEnabled()) {
      return
    }

    JobScheduler.getScheduler().scheduleWithFixedDelay(
      { reportNow() },
      INITIAL_DELAY.toLong(), PERIOD_DELAY.toLong(), TimeUnit.MINUTES
    )
  }

  override fun reportNow() {
    val osEvents: MutableList<MetricEvent> = ArrayList()

    /** Record OS name in both old and new format to have a smooth transition on the server **/
    val dataOS = newDataWithOsVersion()
    osEvents.add(newMetric(getOSName(), dataOS))
    osEvents.add(newMetric("os.name", dataOS.copy().addData("name", getOSName())))

    /** writing current os timezone as os.timezone event_id **/
    val currentZoneOffset = OffsetDateTime.now().offset
    val currentZoneOffsetFeatureUsageData = FeatureUsageData().addData("value", currentZoneOffset.toString())
    osEvents.add(newMetric("os.timezone" , currentZoneOffsetFeatureUsageData))
    FUStateUsagesLogger.logStateEvents(OS_GROUP, osEvents)
  }

  private fun newDataWithOsVersion(): FeatureUsageData {
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
      SystemInfo.isFreeBSD -> "FreeBSD"
      SystemInfo.isSolaris -> "Solaris"
      else -> "Other"
    }
  }
}
