// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.util.text.StringUtil

class PresentationAssistant233 {
  fun migratePlugin(options: PluginMigrationOptions) {
    if (StringUtil.compareVersionNumbers(options.currentProductVersion, "233") >= 0) {
      options.pluginsToMigrate.removeIf { it.pluginId.idString == "org.nik.presentation-assistant" }
    }
  }
}