// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.execution.Executor
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.addPluginInfoTo
import com.intellij.internal.statistic.utils.getPluginType
import com.intellij.internal.statistic.utils.getProjectId
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*

/**
 * <p>FeatureUsageData represents additional data for reported event.</p>
 *
 * <h3>Example</h3>
 *
 * <p>My usage collector collects actions invocations. <i>"my.foo.action"</i> could be invoked from one of the following contexts:
 * "main.menu", "context.menu", "my.dialog", "all-actions-run".</p>
 *
 * <p>If I write {@code FUCounterUsageLogger.logEvent("my.foo.action", "bar")}, I'll know how many times the action "bar" was invoked (e.g. 239)</p>
 *
 * <p>If I write {@code FUCounterUsageLogger.logEvent("my.foo.action", "bar", new FeatureUsageData().addPlace(place))}, I'll get the same
 * total count of action invocations (239), but I'll also know that the action was called 3 times from "main.menu", 235 times from "my.dialog" and only once from "context.menu".
 * <br/>
 * </p>
 */
class FeatureUsageData {
  private var data: MutableMap<String, Any> = HashMap()

  companion object {
    // don't list "version" as "platformDataKeys" because it format depends a lot on the tool
    val platformDataKeys: MutableList<String> = Arrays.asList(
      "plugin", "project", "os", "plugin_type", "lang", "current_file", "input_event", "place"
    )
  }

  fun addFeatureContext(context: FUSUsageContext?): FeatureUsageData {
    if (context != null) {
      data.putAll(context.data)
    }
    return this
  }

  /**
   * Project data is added automatically for project state collectors and project-wide counter events.
   *
   * @see com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
   * @see com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger.logEvent(Project, String, String)
   * @see com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger.logEvent(Project, String, String, FeatureUsageData)
   */
  fun addProject(project: Project?): FeatureUsageData {
    if (project != null) {
      data["project"] = getProjectId(project)
    }
    return this
  }

  fun addVersionByString(version: String?): FeatureUsageData {
    if (version == null) {
      data["version"] = "unknown"
    }
    else {
      addVersion(Version.parseVersion(version))
    }
    return this
  }

  fun addVersion(version: Version?): FeatureUsageData {
    data["version"] = if (version != null) "${version.major}.${version.minor}" else "unknown.format"
    return this
  }

  /**
   * Group by OS will be available without adding OS explicitly to event data.
   */
  @Deprecated("Don't add OS to event data")
  fun addOS(): FeatureUsageData {
    data["os"] = getOS()
    return this
  }

  private fun getOS(): String {
    if (SystemInfo.isWindows) return "Windows"
    if (SystemInfo.isMac) return "Mac"
    return if (SystemInfo.isLinux) "Linux" else "Other"
  }

  fun addPluginInfo(info: PluginInfo?): FeatureUsageData {
    info?.let {
      addPluginInfoTo(info, data)
    }
    return this
  }

  fun addLanguage(id: String?): FeatureUsageData {
    id?.let {
      addLanguage(Language.findLanguageByID(id))
    }
    return this
  }

  fun addLanguage(language: Language?): FeatureUsageData {
    return addLanguageInternal("lang", language)
  }

  fun addCurrentFile(language: Language?): FeatureUsageData {
    return addLanguageInternal("current_file", language)
  }

  private fun addLanguageInternal(fieldName: String, language: Language?): FeatureUsageData {
    language?.let {
      val type = getPluginType(language.javaClass)
      if (type.isSafeToReport()) {
        data[fieldName] = language.id
      }
      else {
        data[fieldName] = "third.party"
      }
    }
    return this
  }

  fun addInputEvent(event: AnActionEvent): FeatureUsageData {
    val inputEvent = ShortcutDataProvider.getActionEventText(event)
    if (inputEvent != null && StringUtil.isNotEmpty(inputEvent)) {
      data["input_event"] = inputEvent
    }
    return this
  }

  fun addInputEvent(event: KeyEvent): FeatureUsageData {
    val inputEvent = ShortcutDataProvider.getKeyEventText(event)
    if (inputEvent != null && StringUtil.isNotEmpty(inputEvent)) {
      data["input_event"] = inputEvent
    }
    return this
  }

  fun addInputEvent(event: MouseEvent): FeatureUsageData {
    val inputEvent = ShortcutDataProvider.getMouseEventText(event)
    if (inputEvent != null && StringUtil.isNotEmpty(inputEvent)) {
      data["input_event"] = inputEvent
    }
    return this
  }

  fun addPlace(place: String?): FeatureUsageData {
    if (place == null) return this

    var reported = ActionPlaces.UNKNOWN
    if (isCommonPlace(place)) {
      reported = place
    }
    else if (ActionPlaces.isPopupPlace(place)) {
      reported = ActionPlaces.POPUP
    }
    data["place"] = reported
    return this
  }

  private fun isCommonPlace(place: String): Boolean {
    return ActionPlaces.isCommonPlace(place) || ToolWindowContentUi.POPUP_PLACE == place
  }

  fun addExecutor(executor: Executor): FeatureUsageData {
    return addData("executor", executor.id)
  }

  fun addValue(value: Any): FeatureUsageData {
    if (value is String || value is Boolean || value is Int || value is Long || value is Float || value is Double) {
      return addDataInternal("value", value)
    }
    return addData("value", value.toString())
  }

  fun addEnabled(enabled: Boolean): FeatureUsageData {
    return addData("enabled", enabled)
  }

  fun addCount(count: Int): FeatureUsageData {
    return addData("count", count)
  }

  fun addData(key: String, value: Boolean): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: Int): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: Long): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: Float): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: Double): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: String): FeatureUsageData {
    return addDataInternal(key, value)
  }

  private fun addDataInternal(key: String, value: Any): FeatureUsageData {
    if (ApplicationManager.getApplication().isUnitTestMode || !platformDataKeys.contains(key)) data[key] = value

    return this
  }

  fun build(): Map<String, Any> {
    if (data.isEmpty()) {
      return Collections.emptyMap()
    }
    return data
  }

  fun merge(next: FeatureUsageData, prefix: String): FeatureUsageData {
    for ((key, value) in next.build()) {
      val newKey = if (key.startsWith("data_")) "$prefix$key" else key
      data[newKey] = value
    }
    return this
  }

  fun copy(): FeatureUsageData {
    val result = FeatureUsageData()
    for ((key, value) in data) {
      result.data[key] = value
    }
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FeatureUsageData

    if (data != other.data) return false

    return true
  }

  override fun hashCode(): Int {
    return data.hashCode()
  }

  override fun toString(): String {
    return data.toString()
  }
}

fun newData(project: Project?, context: FUSUsageContext?): Map<String, Any> {
  if (project == null && context == null) return Collections.emptyMap()

  return FeatureUsageData().addProject(project).addFeatureContext(context).build()
}