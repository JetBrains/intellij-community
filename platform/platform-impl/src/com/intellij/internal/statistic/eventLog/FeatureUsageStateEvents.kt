// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import java.util.*
import javax.xml.bind.Element

object FeatureUsageStateEvents {
  private const val CONFIG_RECORDER_ID = "config-recorder"
  private val defaultFilter = SkipDefaultsSerializationFilter()

  fun logConfigurationState(componentName: String,
                            component: PersistentStateComponent<*>,
                            isAppLevel: Boolean) {
    val state = component.state ?: return
    if (state is Element) {
      return
    }

    val accessors = BeanBinding.getAccessors(state.javaClass)
    if (accessors.isEmpty()) {
      return
    }

    val config = HashMap<String, Any>()
    for (accessor in accessors) {
      val type = accessor.genericType
      if (type === Boolean::class.javaPrimitiveType) {
        val value = accessor.read(component.state!!)
        val isNotDefault = defaultFilter.accepts(accessor, component.state!!)
        val content = HashMap<String, Any>()
        content["value"] = value
        if (isNotDefault) {
          content["default"] = false;
        }
        config[accessor.name] = content
      }
    }

    if (!config.isEmpty()) {
      config["app_level"] = isAppLevel
      FeatureUsageLogger.log(CONFIG_RECORDER_ID, componentName, config)
    }
  }
}