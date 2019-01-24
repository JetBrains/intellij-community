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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.util.containers.ContainerUtil
import java.awt.event.KeyEvent
import java.util.*

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

  fun addData(key: String, value: Any): FeatureUsageData {
    data[key] = value
    return this
  }

  fun build(): Map<String, Any> {
    return data
  }

  fun copy(): FeatureUsageData {
    val result = FeatureUsageData()
    for ((key, value) in data) {
      result.addData(key, value)
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