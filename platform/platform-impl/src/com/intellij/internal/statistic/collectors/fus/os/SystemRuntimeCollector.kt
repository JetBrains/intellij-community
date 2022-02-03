// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import com.intellij.diagnostic.VMOptions
import com.intellij.internal.DebugAttachDetector
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.AllowedDuringStartupCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

class SystemRuntimeCollector : ApplicationUsagesCollector(), AllowedDuringStartupCollector {
  private val COLLECTORS = listOf("Serial", "Parallel", "CMS", "G1", "Z", "Shenandoah", "Epsilon", "Other")
  private val ARCHITECTURES = listOf("x86", "x86_64", "arm64", "other", "unknown")
  private val VENDORS = listOf("JetBrains", "Apple", "Oracle", "Sun", "IBM", "Azul", "Other")
  private val VM_OPTIONS = listOf("Xmx", "Xms", "SoftRefLRUPolicyMSPerMB", "ReservedCodeCacheSize")
  private val SYSTEM_PROPERTIES = listOf("splash", "nosplash")
  private val RENDERING_PIPELINES = listOf("Metal", "OpenGL")

  private val GROUP: EventLogGroup = EventLogGroup("system.runtime", 16)
  private val CORES: EventId1<Int> = GROUP.registerEvent("cores", Int("value"))
  private val MEMORY_SIZE: EventId1<Int> = GROUP.registerEvent("memory.size", Int("gigabytes"))
  private val SWAP_SIZE: EventId1<Int> = GROUP.registerEvent("swap.size", Int("gigabytes"))
  private val DISK_SIZE: EventId2<Int, Int> = GROUP.registerEvent("disk.size", Int("index_partition_size"), Int("index_partition_free"))
  private val GC: EventId1<String?> = GROUP.registerEvent("garbage.collector", String("name", COLLECTORS))
  private val JVM: EventId3<Version?, String?, String?> =
    GROUP.registerEvent("jvm", EventFields.VersionByObject, String("arch", ARCHITECTURES), String("vendor", VENDORS))
  private val JVM_OPTION: EventId2<String?, Long> = GROUP.registerEvent("jvm.option", String("name", VM_OPTIONS), Long("value"))
  private val SYSTEM_PROPERTY: EventId2<String?, Boolean> =
    GROUP.registerEvent("jvm.client.properties", String("name", SYSTEM_PROPERTIES), Boolean("value"))
  private val DEBUG_AGENT: EventId1<Boolean> = GROUP.registerEvent("debug.agent", EventFields.Enabled)
  private val RENDERING: EventId1<String?> = GROUP.registerEvent("rendering.pipeline", String("name", RENDERING_PIPELINES))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()

    result += CORES.metric(getCpuCoreCount())

    val (physicalMemory, swapSize) = getPhysicalMemoryAndSwapSize()
    result += MEMORY_SIZE.metric(physicalMemory)
    result += SWAP_SIZE.metric(swapSize)

    val indexVolumeData = getIndexVolumeSizeAndFreeSpace()
    if (indexVolumeData != null) {
      val (size, freeSpace) = indexVolumeData
      result += DISK_SIZE.metric(size, freeSpace)
    }

    result += GC.metric(getGcName())

    // Proper detection implemented only for macOS
    if (SystemInfo.isMac) result += RENDERING.metric(getRenderingPipelineName())

    result += JVM.metric(
      Version(1, JavaVersion.current().feature, 0),
      CpuArch.CURRENT.name.lowercase(Locale.ENGLISH),
      getJavaVendor())

    for (option in collectJvmOptions()) {
      result += JVM_OPTION.metric(option.key, option.value)
    }

    for (property in collectSystemProperties()) {
      result += SYSTEM_PROPERTY.metric(property.key, property.value.toBoolean())
    }

    result += DEBUG_AGENT.metric(DebugAttachDetector.isDebugEnabled())

    return result
  }

  private fun getCpuCoreCount(): Int =
    StatisticsUtil.roundToUpperBound(Runtime.getRuntime().availableProcessors(), intArrayOf(1, 2, 4, 6, 8, 12, 16, 20, 24, 32, 64))

  private fun getPhysicalMemoryAndSwapSize(): Pair<Int, Int> {
    @Suppress("FunctionName") fun GiB(bytes: Long) = (bytes.toDouble() / (1 shl 30)).roundToInt()
    val bean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val physicalMemory = StatisticsUtil.roundToUpperBound(GiB(bean.totalPhysicalMemorySize), intArrayOf(1, 2, 4, 8, 12, 16, 24, 32, 48, 64, 128, 256))
    val swapSize = StatisticsUtil.roundToPowerOfTwo(min(GiB(bean.totalSwapSpaceSize), physicalMemory))
    return physicalMemory to swapSize
  }

  private fun getIndexVolumeSizeAndFreeSpace(): Pair<Int, Int>? {
    try {
      val fileStore = Files.getFileStore(PathManager.getIndexRoot())
      val totalSpace = fileStore.totalSpace
      if (totalSpace > 0L) {
        val size = min(1 shl 14, StatisticsUtil.roundToPowerOfTwo((totalSpace shr 30).toInt()))  // ATM, the biggest popular consumer HDDs are ~16 TB
        val freeSpace = (fileStore.usableSpace * 100.0 / totalSpace).toInt()
        return size to freeSpace
      }
    }
    catch (_: UnsupportedOperationException) { }  // some non-standard FS
    catch (_: SecurityException) { }  // security manager denies reading of FS attributes
    return null
  }

  private fun getGcName(): String {
    for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
      if (gc.name == "MarkSweepCompact" || gc.name == "Copy") return "Serial"       // -XX:+UseSerialGC
      if (gc.name == "PS MarkSweep" || gc.name == "PS Scavenge") return "Parallel"  // -XX:+UseParallelGC
      if (gc.name == "ConcurrentMarkSweep" || gc.name == "ParNew") return "CMS"     // -XX:+UseConcMarkSweepGC
      if (gc.name.startsWith("G1 ")) return "G1"                                    // -XX:+UseG1GC
      if (gc.name == "ZGC") return "Z"                                              // -XX:+UseZGC
      if (gc.name.startsWith("Shenandoah ")) return "Shenandoah"                    // -XX:+UseShenandoahGC
      if (gc.name.startsWith("Epsilon ")) return "Epsilon"                          // -XX:+UseEpsilonGC
    }
    return "Other"
  }

  private fun getRenderingPipelineName() = if (SystemInfo.isMetalRendering) "Metal" else "OpenGL"

  private fun getJavaVendor(): String =
    when {
      SystemInfo.isJetBrainsJvm -> "JetBrains"
      SystemInfo.isOracleJvm -> "Oracle"
      SystemInfo.isIbmJvm -> "IBM"
      SystemInfo.isAzulJvm -> "Azul"
      else -> "Other"
    }

  private fun collectJvmOptions(): Map<String, Long> =
    ManagementFactory.getRuntimeMXBean().inputArguments.asSequence()
      .map { arg ->
        try {
          fun parse(arg: String, start: Int) = VMOptions.parseMemoryOption(arg.substring(start)) shr 20
          fun roundDown(value: Long, vararg steps: Long) = steps.findLast { it <= value } ?: 0
          when {
            arg.startsWith("-Xms") -> "Xms" to roundDown(parse(arg, 4), 64, 128, 256, 512)
            arg.startsWith("-Xmx") -> "Xmx" to roundDown(parse(arg, 4), 512, 750, 1000, 1024, 1500, 2000, 2048, 3000, 4000, 4096, 6000, 8000)
            arg.startsWith("-XX:SoftRefLRUPolicyMSPerMB=") -> "SoftRefLRUPolicyMSPerMB" to roundDown(parse(arg, 28), 50, 100)
            arg.startsWith("-XX:ReservedCodeCacheSize=") -> "ReservedCodeCacheSize" to roundDown(parse(arg, 26), 240, 300, 400, 500)
            else -> null
          }
        }
        catch (e: IllegalArgumentException) {
          null
        }
      }
      .filterNotNull()
      .toMap()

  private fun collectSystemProperties(): Map<String, String> =
    SYSTEM_PROPERTIES.asSequence()
      .map { it to System.getProperty(it) }
      .filter { it.second != null }
      .toMap()
}
