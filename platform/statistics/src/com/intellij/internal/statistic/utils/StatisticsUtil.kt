// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName

fun addPluginInfoTo(info: PluginInfo, data: MutableMap<String, Any>) {
  data["plugin_type"] = info.type.name
  if (!info.type.isSafeToReport()) return
  val id = info.id
  if (!id.isNullOrEmpty()) {
    data["plugin"] = id
  }
  val version = info.version
  if (!version.isNullOrEmpty()) {
    data["plugin_version"] = version
  }
}


object StatisticsUtil {
  private const val kilo = 1000
  private const val mega = kilo * kilo

  @JvmStatic
  fun getProjectId(project: Project): String {
    return EventLogConfiguration.anonymize(project.getProjectCacheFileName())
  }

  /**
   * Anonymizes sensitive project properties by rounding it to the next power of two
   * @see com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
   */
  @JvmStatic
  fun getNextPowerOfTwo(value: Int): Int = if (value <= 1) 1 else Integer.highestOneBit(value - 1) shl 1

  /**
   * Anonymizes sensitive project properties by rounding it to the next value in steps list.
   * @see com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
   */
  fun getCountingStepName(value: Int, steps: List<Int>): String {
    if (steps.isEmpty()) return value.toString()
    if (value < steps[0]) return "<" + steps[0]

    var stepIndex = 0
    while (stepIndex < steps.size - 1) {
      if (value < steps[stepIndex + 1]) break
      stepIndex++
    }

    val step = steps[stepIndex]
    val addPlus = stepIndex == steps.size - 1 || steps[stepIndex + 1] != step + 1
    return humanize(step) + if (addPlus) "+" else ""
  }

  private fun humanize(number: Int): String {
    if (number == 0) return "0"
    val m = number / mega
    val k = (number % mega) / kilo
    val r = (number % kilo)
    val ms = if (m > 0) "${m}M" else ""
    val ks = if (k > 0) "${k}K" else ""
    val rs = if (r > 0) "${r}" else ""
    return ms + ks + rs
  }
}