// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.os

import com.intellij.internal.DebugAttachDetector
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

class SystemRuntimeCollector : ApplicationUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val result = HashSet<MetricEvent>()
    result.add(CORES.metric(StatisticsUtil.getUpperBound(Runtime.getRuntime().availableProcessors(),
                                                         intArrayOf(1, 2, 4, 6, 8, 12, 16, 20, 24, 32, 64))))

    val osMxBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val totalPhysicalMemory = StatisticsUtil.getUpperBound((osMxBean.totalPhysicalMemorySize.toDouble() / (1 shl 30)).roundToInt(),
                                                           intArrayOf(1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 128, 256))
    result.add(MEMORY_SIZE.metric(totalPhysicalMemory))

    var totalSwapSize = (osMxBean.totalSwapSpaceSize.toDouble() / (1 shl 30)).roundToInt()
    totalSwapSize = min(totalSwapSize, totalPhysicalMemory)
    result.add(SWAP_SIZE.metric(if (totalSwapSize > 0) StatisticsUtil.getNextPowerOfTwo(totalSwapSize) else 0))

    for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
      result.add(GC.metric(gc.name))
    }

    result.add(JVM.metric(
      Version(1, JavaVersion.current().feature, 0),
      CpuArch.CURRENT.name.toLowerCase(Locale.ENGLISH),
      getJavaVendor())
    )
    val options: HashMap<String, Long> = collectJvmOptions()
    for (option in options) {
      result.add(JVM_OPTION.metric(option.key, option.value))
    }
    result.add(DEBUG_AGENT.metric(DebugAttachDetector.isDebugEnabled()))
    return result
  }

  private fun collectJvmOptions(): HashMap<String, Long> {
    val options: HashMap<String, Long> = hashMapOf()
    for (argument in ManagementFactory.getRuntimeMXBean().inputArguments) {
      val data = convertOptionToData(argument)
      if (data != null) {
        options[data.first] = data.second
      }
    }
    return options
  }

  private fun getJavaVendor() : String {
    return when {
      SystemInfo.isJetBrainsJvm -> "JetBrains"
      SystemInfo.isOracleJvm -> "Oracle"
      SystemInfo.isIbmJvm -> "IBM"
      SystemInfo.isAzulJvm -> "Azul"
      else -> "Other"
    }
  }

  companion object {
    private val knownOptions = ContainerUtil.newHashSet(
      "-Xms", "-Xmx", "-XX:SoftRefLRUPolicyMSPerMB", "-XX:ReservedCodeCacheSize"
    )

    private val GROUP: EventLogGroup = EventLogGroup("system.runtime", 9)
    private val DEBUG_AGENT: EventId1<Boolean> = GROUP.registerEvent("debug.agent", EventFields.Enabled)
    private val CORES: EventId1<Int> = GROUP.registerEvent("cores", EventFields.Int("value"))
    private val MEMORY_SIZE: EventId1<Int> = GROUP.registerEvent("memory.size", EventFields.Int("gigabytes"))
    private val SWAP_SIZE: EventId1<Int> = GROUP.registerEvent("swap.size", EventFields.Int("gigabytes"))
    private val GC: EventId1<String?> = GROUP.registerEvent("garbage.collector",
      EventFields.String(
        "name",
        arrayListOf("Shenandoah", "G1_Young_Generation", "G1_Old_Generation", "Copy",
                    "MarkSweepCompact", "PS_MarkSweep", "PS_Scavenge", "ParNew", "ConcurrentMarkSweep")
      )
    )
    private val JVM: EventId3<Version?, String?, String?> = GROUP.registerEvent("jvm",
      EventFields.VersionByObject,
      EventFields.String("arch", arrayListOf("x86", "x86_64", "arm64", "other", "unknown")),
      EventFields.String("vendor", arrayListOf( "JetBrains", "Apple", "Oracle", "Sun", "IBM", "Azul", "Other"))
    )
    private val JVM_OPTION: EventId2<String?, Long> = GROUP.registerEvent("jvm.option",
      EventFields.String("name", arrayListOf("Xmx", "Xms", "SoftRefLRUPolicyMSPerMB", "ReservedCodeCacheSize")),
      EventFields.Long("value")
    )

    fun convertOptionToData(arg: String): Pair<String, Long>? {
      val value = getMegabytes(arg).toLong()
      if (value < 0) return null

      when {
        arg.startsWith("-Xmx") -> {
          return "Xmx" to roundDown(value, 512, 750, 1000, 1024, 1500, 2000, 2048, 3000, 4000, 4096, 6000, 8000)
        }
        arg.startsWith("-Xms") -> {
          return "Xms" to roundDown(value, 64, 128, 256, 512)
        }
        arg.startsWith("-XX:SoftRefLRUPolicyMSPerMB") -> {
          return "SoftRefLRUPolicyMSPerMB" to roundDown(value, 50, 100)
        }
        arg.startsWith("-XX:ReservedCodeCacheSize") -> {
          return "ReservedCodeCacheSize" to roundDown(value, 240, 300, 400, 500)
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
