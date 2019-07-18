// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

import com.intellij.ide.plugins.*
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.beans.newCounterMetric
import com.intellij.internal.statistic.beans.newBooleanMetric
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.addCounterIfDiffers
import com.intellij.internal.statistic.beans.addIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.newData
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TimeoutCachedValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ObjectIntHashMap
import gnu.trove.THashSet
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

fun getProjectId(project: Project): String {
  return EventLogConfiguration.anonymize(project.getProjectCacheFileName())
}

fun createData(project: Project?, context: FUSUsageContext?): Map<String, Any> {
  return newData(project, context)
}

fun addPluginInfoTo(info: PluginInfo, data : MutableMap<String, Any>) {
  data["plugin_type"] = info.type.name
  if (info.type.isSafeToReport() && info.id != null && StringUtil.isNotEmpty(info.id)) {
    data["plugin"] = info.id
  }
}

fun isDevelopedByJetBrains(pluginId: PluginId?): Boolean {
  val plugin = PluginManager.getPlugin(pluginId)
  return plugin == null || PluginManagerMain.isDevelopedByJetBrains(plugin.vendor)
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

private val safeToReportPluginIds: Getter<Set<String>> = TimeoutCachedValue(1, TimeUnit.HOURS) {
  val plugins = collectSafePluginDescriptors()
  val ids = mutableSetOf<String>()
  plugins.mapNotNullTo(ids) { descriptor: PluginDescriptor -> descriptor.pluginId?.idString }
  ids
};

/**
 * We are safe to report only plugins which are developed by JetBrains or in our official plugin repository due to GDPR
 */
private fun collectSafePluginDescriptors(): List<IdeaPluginDescriptor> {
  // before loading default repository plugins lets check it's not changed, and is really official JetBrains repository
  if (ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
    try {
      val cached = RepositoryHelper.loadCachedPlugins()
      if (cached != null) {
        val plugins = ArrayList<IdeaPluginDescriptor>()
        plugins.addAll(cached)
        plugins.addAll(getBundledJetBrainsPluginDescriptors())
        return plugins
      }
      else {
        // schedule plugins loading, will take them the next time
        ApplicationManager.getApplication().executeOnPooledThread {
          try {
            RepositoryHelper.loadPlugins(null)
          }
          catch (ignored: IOException) {
          }
        }
        return emptyList() //report nothing until repo plugins loaded
      }
    }
    catch (ignored: IOException) {
    }
  }

  return getBundledJetBrainsPluginDescriptors()
}

/**
 * Note that there may be private custom IDE build with bundled custom plugins;
 * so isBundled check is not enough
 */
private fun getBundledJetBrainsPluginDescriptors(): List<IdeaPluginDescriptor> {
  return PluginManager.getPlugins().filter { it.isBundled && PluginManagerMain.isDevelopedByJetBrains(it) }.toList()
}

/**
 * Checks this plugin is created by JetBrains or from official repository, so API from it may be reported
 */
fun isSafeToReportFrom(descriptor: IdeaPluginDescriptor?): Boolean {
  if (descriptor == null) {
    return false
  }
  if (isDevelopedByJetBrains(descriptor.pluginId)) {
    return true
  }

  // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance -
  // they are also considered bundled) would be reported
  return !descriptor.isBundled && isSafeToReport(descriptor.pluginId?.idString)
}

/**
 * Checks plugin with same id is created by JetBrains or from official repository, so pluginId may be reported.
 *
 * On the very first invocation may need to load cached plugins later; in that case no plugins are considered safe
 */
fun isSafeToReport(pluginId: String?): Boolean {
  return pluginId != null && safeToReportPluginIds.get().contains(pluginId)
}

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginType(clazz: Class<*>): PluginType {
  val pluginId = PluginManagerCore.getPluginByClassName(clazz.name) ?: return PluginType.PLATFORM
  val plugin = PluginManager.getPlugin(pluginId) ?: return PluginType.UNKNOWN

  if (PluginManagerMain.isDevelopedByJetBrains(plugin)) {
    return if (plugin.isBundled) PluginType.JB_BUNDLED else PluginType.JB_NOT_BUNDLED
  }

  // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance -
  // they are also considered bundled) would be reported
  val listed = !plugin.isBundled && isSafeToReport(pluginId.idString)
  return if (listed) PluginType.LISTED else PluginType.NOT_LISTED
}

private class DelayModificationTracker internal constructor(delay: Long, unit: TimeUnit) : ModificationTracker {

  private val myStamp = System.currentTimeMillis()
  private val myDelay: Long = TimeUnit.MILLISECONDS.convert(delay, unit)

  override fun getModificationCount(): Long {
    val diff = System.currentTimeMillis() - (myStamp + myDelay)
    return if (diff > 0) diff else 0
  }
}
