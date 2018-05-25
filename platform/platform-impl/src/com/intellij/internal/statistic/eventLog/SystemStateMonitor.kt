// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.concurrency.JobScheduler
import com.intellij.internal.statistic.collectors.fus.os.OsVersionUsageCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.lang.JavaVersion
import java.util.concurrent.TimeUnit

class SystemStateMonitor : StartupActivity {
  private val INITIAL_DELAY = 5
  private val PERIOD_DELAY = 24 * 60

  override fun runActivity(project: Project) {
    JobScheduler.getScheduler().scheduleWithFixedDelay(
      { logSystemEvent() },
      INITIAL_DELAY.toLong(), PERIOD_DELAY.toLong(), TimeUnit.MINUTES
    )
  }

  private fun logSystemEvent() {
    val os = HashMap<String, Any>()
    os["name"] = getOSName()
    os["version"] = getOSVersion()

    val jvm = HashMap<String, Any>()
    jvm["vendor"] = System.getProperty("java.vendor", "Unknown")
    jvm["version"] = "1." + JavaVersion.current().feature

    val info = HashMap<String, Any>()
    info["os"] = os
    info["jvm"] = jvm
    FeatureUsageLogger.log("state-monitoring", "system", info)
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
    return SystemInfo.OS_VERSION
  }
}
