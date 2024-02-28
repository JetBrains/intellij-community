// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.tryReadPluginIdsFromFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Files

class AIAssistant241 : PluginMigration() {
  private val PLUGIN_ID = "com.intellij.ml.llm"
  private val PRODUCT_IDS = setOf("IU", "PY", "WS", "RM", "PS", "GO", "CL", "RD", "RR")

  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (descriptor.options.previousVersion != "2023.3") return

    val productCode = PluginManagerCore.buildNumber.productCode
    if (!PRODUCT_IDS.contains(productCode)) return

    val oldConfigDir = descriptor.options.oldConfigDir
    val disabledPluginsConfig = oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)

    val disabledIds: Set<PluginId> = if (Files.exists(disabledPluginsConfig)) {
      tryReadPluginIdsFromFile(disabledPluginsConfig, Logger.getInstance(AIAssistant241::class.java))
    }
    else {
      emptySet()
    }

    if (!disabledIds.contains(PluginId.getId(PLUGIN_ID))) {
      descriptor.addPluginIfNeeded(PLUGIN_ID)
    }
  }
}