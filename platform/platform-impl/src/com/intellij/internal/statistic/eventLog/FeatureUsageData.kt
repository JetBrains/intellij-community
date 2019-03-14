// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginType
import com.intellij.internal.statistic.utils.getProjectId
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.util.containers.ContainerUtil
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
  private var data: MutableMap<String, Any> = ContainerUtil.newHashMap<String, Any>()

  fun addFeatureContext(context: FUSUsageContext?): FeatureUsageData {
    if (context != null) {
      data.putAll(context.data)
    }
    return this
  }

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

  fun addOS(): FeatureUsageData {
    data["os"] = getOS()
    return this
  }

  private fun getOS(): String {
    if (SystemInfo.isWindows) return "Windows"
    if (SystemInfo.isMac) return "Mac"
    return if (SystemInfo.isLinux) "Linux" else "Other"
  }

  fun addPluginInfo(info: PluginInfo): FeatureUsageData {
    data["plugin_type"] = info.type.name
    if (info.type.isSafeToReport() && info.id != null && StringUtil.isNotEmpty(info.id)) {
      data["plugin"] = info.id
    }
    return this
  }

  fun addLanguage(language: Language): FeatureUsageData {
    val type = getPluginType(language.javaClass)
    if (type.isSafeToReport()) {
      data["lang"] = language.id
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

  fun addData(key: String, value: Boolean): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: Int): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: Long): FeatureUsageData {
    return addDataInternal(key, value)
  }

  fun addData(key: String, value: String): FeatureUsageData {
    return addDataInternal(key, value)
  }

  private fun addDataInternal(key: String, value: Any): FeatureUsageData {
    data[key] = value
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
      addDataInternal(newKey, value)
    }
    return this
  }

  fun copy(): FeatureUsageData {
    val result = FeatureUsageData()
    for ((key, value) in data) {
      result.addDataInternal(key, value)
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
}

fun newData(project: Project?, context: FUSUsageContext?): Map<String, Any> {
  if (project == null && context == null) return Collections.emptyMap()

  return FeatureUsageData().addProject(project).addFeatureContext(context).build()
}