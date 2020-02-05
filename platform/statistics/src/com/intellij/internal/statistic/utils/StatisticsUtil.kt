// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.beans.*
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.TimeoutCachedValue
import com.intellij.util.containers.ObjectIntHashMap
import gnu.trove.THashSet
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

fun getProjectId(project: Project): String {
  return EventLogConfiguration.anonymize(project.getProjectCacheFileName())
}

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

fun isDevelopedByJetBrains(pluginId: PluginId?): Boolean {
  val plugin = PluginManagerCore.getPlugin(pluginId)
  return plugin == null || PluginManager.isDevelopedByJetBrains(plugin.vendor)
}

/**
 * @deprecated will be deleted in 2019.3
 *
 * Report setting name as event id and enabled/disabled state in event data with MetricEvent class.
 * @see newBooleanMetric(java.lang.String, boolean)*
 */
@Deprecated("Report enabled or disabled setting as MetricEvent")
fun getBooleanUsage(key: String, value: Boolean): UsageDescriptor {
  return UsageDescriptor(key + if (value) ".enabled" else ".disabled", 1)
}

/**
 * @deprecated will be deleted in 2019.3
 *
 * Report setting name as event id and setting value in event data with MetricEvent class.
 * @see newMetric(java.lang.String, Enum)*
 */
@Deprecated("Report enum settings as MetricEvent")
fun getEnumUsage(key: String, value: Enum<*>?): UsageDescriptor {
  return UsageDescriptor(key + "." + value?.name?.toLowerCase(Locale.ENGLISH), 1)
}

/**
 * @deprecated will be deleted in 2019.3
 * This method should be used only for a transition period for existing counter metrics.
 * New metrics should report absolute counter value by
 * @see newCounterMetric(java.lang.String, int)
 *
 * Constructs a proper UsageDescriptor for a counting value.
 * NB:
 * (1) the list of steps must be sorted ascendingly; If it is not, the result is undefined.
 * (2) the value should lay somewhere inside steps ranges. If it is below the first step, the following usage will be reported:
 * `git.commit.count.<1`.
 *
 * @key   The key prefix which will be appended with "." and range code.
 * @steps Limits of the ranges. Each value represents the start of the next range. The list must be sorted ascendingly.
 * @value Value to be checked among the given ranges.
 */
fun getCountingUsage(key: String, value: Int, steps: List<Int>) : UsageDescriptor {
  return UsageDescriptor("$key." + getCountingStepName(value, steps), 1)
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
private val mega = kilo * kilo

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

/**
 * @deprecated will be deleted in 2019.3
 *
 * Report setting name as event id and setting value in event data with MetricEvent class.
 *
 * @see addIfDiffers
 * @see addBoolIfDiffers
 * @see addCounterIfDiffers
 */
fun <T> addIfDiffers(set: MutableSet<in UsageDescriptor>, settingsBean: T, defaultSettingsBean: T,
                     valueFunction: (T) -> Any, featureIdPrefix: String) {
  addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, { "$featureIdPrefix.$it" })
}

/**
 * @deprecated will be deleted in 2019.3
 *
 * Report setting name as event id and setting value in event data with MetricEvent class.
 *
 * @see addIfDiffers
 * @see addBoolIfDiffers
 * @see addCounterIfDiffers
 */
fun <T, V> addIfDiffers(set: MutableSet<in UsageDescriptor>, settingsBean: T, defaultSettingsBean: T,
                        valueFunction: (T) -> V,
                        featureIdFunction: (V) -> String) {
  val value = valueFunction(settingsBean)
  val defaultValue = valueFunction(defaultSettingsBean)
  if (!Comparing.equal(value, defaultValue)) {
    set.add(UsageDescriptor(featureIdFunction(value), 1))
  }
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
  if (PluginManager.isDevelopedByJetBrains(descriptor)) {
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

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginType(clazz: Class<*>): PluginType {
  val plugin = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(clazz.name) ?: return PluginType.PLATFORM
  if (PluginManager.isDevelopedByJetBrains(plugin)) {
    return if (plugin.isBundled) PluginType.JB_BUNDLED else PluginType.JB_NOT_BUNDLED
  }

  // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance -
  // they are also considered bundled) would be reported
  val listed = !plugin.isBundled && isSafeToReportFrom(plugin)
  return if (listed) PluginType.LISTED else PluginType.NOT_LISTED
}