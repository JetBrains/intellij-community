// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.os

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.containers.ContainerUtil
import java.lang.management.ManagementFactory
import java.util.*

class SystemRuntimeCollector : ApplicationUsagesCollector() {

  override fun getGroupId(): String {
    return "system.runtime"
  }

  override fun getMetrics(): Set<MetricEvent> {
    val result = HashSet<MetricEvent>()
    Runtime.getRuntime().totalMemory()
    val cores = Runtime.getRuntime().availableProcessors()
    result.add(newMetric("cores", cores, null))

    for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
      result.add(newMetric("garbage.collector", FeatureUsageData().addData("name", gc.name)))
    }

    result.add(newMetric("jvm.bit", if (SystemInfo.is32Bit) "32" else "64"))
    for (argument in ManagementFactory.getRuntimeMXBean().inputArguments) {
      val data = convertOptionToData(argument)
      if (data != null) {
        result.add(newMetric("jvm.option", data))
      }
    }
    return result
  }

  companion object {
    private val knownOptions = ContainerUtil.newHashSet(
      "-Xms", "-Xmx", "-XX:SoftRefLRUPolicyMSPerMB", "-XX:ReservedCodeCacheSize"
    )

    fun convertOptionToData(arg: String): FeatureUsageData? {
      val value = getMegabytes(arg).toLong()
      if (value < 0) return null

      when {
        arg.startsWith("-Xmx") -> {
          return FeatureUsageData().
            addData("name", "Xmx").
            addData("value", roundDown(value, 512, 750, 1000, 1024, 1500, 2000, 2048, 3000, 4000, 4096, 6000, 8000))
        }
        arg.startsWith("-Xms") -> {
          return FeatureUsageData().
            addData("name", "Xms").
            addData("value", roundDown(value, 64, 128, 256, 512))
        }
        arg.startsWith("-XX:SoftRefLRUPolicyMSPerMB") -> {
          return FeatureUsageData().
            addData("name", "SoftRefLRUPolicyMSPerMB").
            addData("value", roundDown(value, 50, 100))
        }
        arg.startsWith("-XX:ReservedCodeCacheSize") -> {
          return FeatureUsageData().
            addData("name", "ReservedCodeCacheSize").
            addData("value", roundDown(value, 240, 300, 400, 500))
        }
        else -> {
          return null
        }
      }
    }

    private fun getMegabytes(s: String): Int {
      var num = knownOptions.firstOrNull { s.startsWith(it) }
        ?.let { s.substring(it.length).toUpperCase().trim() }

      if (num == null) return -1
      if (num.startsWith("=")) num = num.substring(1)

      if (num.last().isDigit()) {
        return try {
          Integer.parseInt(num)
        }
        catch (e: Exception) {
          -1
        }
      }

      try {
        val size = Integer.parseInt(num.substring(0, num.length - 1))
        when (num.last()) {
          'B' -> return size / (1024 * 1024)
          'K' -> return size / 1024
          'M' -> return size
          'G' -> return size * 1024
        }
      }
      catch (e: Exception) {
        return -1
      }

      return -1
    }

    fun roundDown(value: Long, vararg steps: Long): Long {
      val length = steps.size
      if (length == 0 || steps[0] < 0) return -1

      var ind = 0
      while (ind < length && value >= steps[ind]) {
        ind++
      }
      return if (ind == 0) 0 else steps[ind - 1]
    }
  }
}