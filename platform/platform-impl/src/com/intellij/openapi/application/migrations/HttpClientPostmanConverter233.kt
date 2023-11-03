// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.util.text.StringUtil

class HttpClientPostmanConverter233 {
  private val POSTMAN_CONVERTER_ID = "com.intellij.restClient.postmanConverter"

  fun migratePlugins(options: PluginMigrationOptions) {
    if (StringUtil.compareVersionNumbers(options.currentProductVersion, "233") >= 0 &&
        options.pluginsToMigrate.any { it.pluginId.idString == POSTMAN_CONVERTER_ID }
    ) {
      options.pluginsToMigrate.removeIf { it.pluginId.idString == POSTMAN_CONVERTER_ID }
    }
  }
}