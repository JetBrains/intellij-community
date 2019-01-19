// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginType
import com.intellij.internal.statistic.utils.getProjectId
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import java.util.*

class FeatureUsageDataBuilder {
  private var data: MutableMap<String, Any> = ContainerUtil.newHashMap<String, Any>()

  fun addFeatureContext(context: FUSUsageContext?): FeatureUsageDataBuilder {
    if (context != null) {
      data.putAll(context.data)
    }
    return this
  }

  fun addProject(project: Project?): FeatureUsageDataBuilder {
    if (project != null) {
      data["project"] = getProjectId(project)
    }
    return this
  }

  fun addPluginInfo(info: PluginInfo): FeatureUsageDataBuilder {
    data["plugin_type"] = info.type.name
    if (info.type.isSafeToReport() && info.id != null && StringUtil.isNotEmpty(info.id)) {
      data["plugin"] = info.id
    }
    return this
  }

  fun addLanguage(language: Language): FeatureUsageDataBuilder {
    val type = getPluginType(language.javaClass)
    if (type.isSafeToReport()) {
      data["lang"] = language.id
    }
    return this
  }

  fun addPlace(place: String): FeatureUsageDataBuilder {
    data["place"] = place
    return this
  }

  fun addData(key: String, value: Any): FeatureUsageDataBuilder {
    data[key] = value
    return this
  }

  fun createData(): Map<String, Any> {
    return data
  }
}

fun newData(project: Project?, context: FUSUsageContext?): Map<String, Any> {
  if (project == null && context == null) return Collections.emptyMap()

  return FeatureUsageDataBuilder().addProject(project).addFeatureContext(context).createData()
}