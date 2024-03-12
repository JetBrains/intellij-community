// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.tryReadPluginIdsFromFile
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files

internal class AIAssistant241 : PluginMigration() {
  private val PLUGIN_ID = "com.intellij.ml.llm"
  private val PRODUCT_IDS = setOf("IU", "PY", "WS", "DG", "RM", "PS", "GO", "CL", "RD", "RR", "QA")

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
    else {
      descriptor.removePlugin(PLUGIN_ID)
      descriptor.removePluginToDownload(PLUGIN_ID)

      try {
        Files.createFile(descriptor.options.newConfigDir.resolve(NOT_MIGRATED_FILENAME))
      }
      catch (_: Throwable) {
      }
    }
  }
}

private const val NOT_MIGRATED_FILENAME = ".ai-migration-disabled"

@ApiStatus.Internal
fun isAIDisabledBeforeMigrated(): Boolean {
  return Files.exists(PathManager.getConfigDir().resolve(NOT_MIGRATED_FILENAME))
}