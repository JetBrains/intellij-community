// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.impl.presentationAssistant.PresentationAssistant
import com.intellij.platform.ide.impl.presentationAssistant.PresentationAssistantOld

class PresentationAssistant233 {
  fun migratePlugin(options: PluginMigrationOptions) {
    if (StringUtil.compareVersionNumbers(options.currentProductVersion, "233") >= 0) {
      val pluginDescriptor = options.pluginsToMigrate.find { it.pluginId.idString == "org.nik.presentation-assistant" }
      if (pluginDescriptor != null) {
        val state = PresentationAssistant.INSTANCE.state
        state.showActionDescriptions = pluginDescriptor.isEnabled
        options.pluginsToMigrate.removeIf { it.pluginId.idString == "org.nik.presentation-assistant" }
        val statePlugin = PresentationAssistantOld.INSTANCE.state
        state.horizontalAlignment = statePlugin.horizontalAlignment.id
        state.verticalAlignment = statePlugin.verticalAlignment.id
        if (statePlugin.mainKeymap.getKeymap() != null) {
          state.mainKeymapName = statePlugin.mainKeymap.name
          state.mainKeymapLabel = statePlugin.mainKeymap.displayText
        }
        if (statePlugin.alternativeKeymap.getKeymap() != null) {
          state.showAlternativeKeymap = true
          state.alternativeKeymapName = statePlugin.alternativeKeymap.name
          state.alternativeKeymapLabel = statePlugin.alternativeKeymap.displayText
        }
      }
    }
  }
}