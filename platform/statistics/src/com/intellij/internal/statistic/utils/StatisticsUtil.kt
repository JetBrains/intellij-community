// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginManager
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.TimeoutCachedValue
import com.intellij.util.containers.ObjectIntHashMap
import gnu.trove.THashSet
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

fun addPluginInfoTo(info: PluginInfo, data : MutableMap<String, Any>) {
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

/**
 * [getCountingUsage] with steps (0, 1, 2, 3, 5, 10, 15, 30, 50, 100, 500, 1000, 5000, 10000, ...)
 */
fun getCountingUsage(key: String, value: Int): UsageDescriptor {
  if (value > Int.MAX_VALUE / 10) return UsageDescriptor("$key.MANY", 1)
  if (value < 0) return UsageDescriptor("$key.<0", 1)
  if (value < 3) return UsageDescriptor("$key.$value", 1)

  val fixedSteps = listOf(3, 5, 10, 15, 30, 50)

  var step = fixedSteps.last { it <= value }
  while (true) {
    if (value < step * 2) break
    step *= 2
    if (value < step * 5) break
    step *= 5
  }

  val stepName = humanize(step)
  return UsageDescriptor("$key.$stepName+", 1)
}

private const val kilo = 1000
private const val mega = kilo * kilo

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

fun toUsageDescriptors(result: ObjectIntHashMap<String>): Set<UsageDescriptor> {
  if (result.isEmpty) {
    return emptySet()
  }
  else {
    val descriptors = THashSet<UsageDescriptor>(result.size())
    result.forEachEntry { key, value ->
      descriptors.add(UsageDescriptor(key, value))
      true
    }
    return descriptors
  }
}

fun merge(first: Set<UsageDescriptor>, second: Set<UsageDescriptor>): Set<UsageDescriptor> {
  if (first.isEmpty()) {
    return second
  }

  if (second.isEmpty()) {
    return first
  }

  val merged = ObjectIntHashMap<String>()
  addAll(merged, first)
  addAll(merged, second)
  return toUsageDescriptors(merged)
}

private fun addAll(result: ObjectIntHashMap<String>, usages: Set<UsageDescriptor>) {
  for (usage in usages) {
    val key = usage.key
    result.put(key, result.get(key, 0) + usage.value)
  }
}

private val pluginIdsFromOfficialJbPluginRepo: Getter<Set<PluginId>> = TimeoutCachedValue(1, TimeUnit.HOURS) {
  // before loading default repository plugins lets check it's not changed, and is really official JetBrains repository
  try {
    val cached = getPluginInfoProvider()?.loadCachedPlugins()
    if (cached != null) {
      return@TimeoutCachedValue cached.mapNotNullTo(HashSet(cached.size)) { it.pluginId }
    }
  }
  catch (ignored: IOException) {
  }

  // schedule plugins loading, will take them the next time
  ApplicationManager.getApplication().executeOnPooledThread {
    try {
      getPluginInfoProvider()?.loadPlugins(null) ?: emptySet<PluginId>()
    }
    catch (ignored: IOException) {
    }
  }

  //report nothing until repo plugins loaded
  emptySet<PluginId>()
}

fun getPluginInfoProvider(): PluginInfoProvider? {
  return ApplicationManager.getApplication()?.let { ServiceManager.getService(PluginInfoProvider::class.java) }
}

/**
 * Checks this plugin is created by JetBrains or from official repository, so API from it may be reported
 */
internal fun isSafeToReportFrom(descriptor: PluginDescriptor): Boolean {
  if (PluginManager.getInstance().isDevelopedByJetBrains(descriptor)) {
    return true
  }
  else if (descriptor.isBundled) {
    // bundled, but not from JetBrains, so, some custom unknown plugin
    return false
  }

  // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance -
  // they are also considered bundled) would be reported
  val pluginId = descriptor.pluginId ?: return false
  return isPluginFromOfficialJbPluginRepo(pluginId)
}

internal fun isPluginFromOfficialJbPluginRepo(pluginId: PluginId?): Boolean {
  // not official JetBrains repository - is used, so, not safe to report
  if (!ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
    return false
  }

  // if in official JetBrains repository, then it is safe to report
  return pluginIdsFromOfficialJbPluginRepo.get().contains(pluginId)
}

object StatisticsUtil {
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
}