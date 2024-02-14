// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.tryReadPluginIdsFromFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Files

class AIAssistant241 : PluginMigration() {
  private val PLUGIN_ID = "com.intellij.ml.llm"
  private val productIds = setOf("IU", "PY", "WS", "RM", "PS", "GO", "CL", "RD", "RR")

  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    val productCode = PluginManagerCore.buildNumber.productCode
    if (!productIds.contains(productCode)) return

    if (StringUtil.compareVersionNumbers(descriptor.options.currentProductVersion, "241") >= 0) {
      val disabledPluginsConfig = descriptor.options.oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)

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
}