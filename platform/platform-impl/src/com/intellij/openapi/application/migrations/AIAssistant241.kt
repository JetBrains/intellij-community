// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private const val PLUGIN_ID = "com.intellij.ml.llm"

internal class AIAssistant241 : PluginMigration() {
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

      createNotMigratedFile(descriptor.options.newConfigDir)
      excludeFromDisabled(descriptor.options.newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME))
    }
  }
}

private fun createNotMigratedFile(newConfigDir: Path) {
  try {
    Files.createFile(newConfigDir.resolve(NOT_MIGRATED_FILENAME))
  }
  catch (_: Throwable) {
  }
}

private fun excludeFromDisabled(disabledPluginsConfig: Path) {
  // we exclude plugin from disabled, so when it is installed again it would be enabled right away
  if (!Files.exists(disabledPluginsConfig)) return

  try {
    val ids = mutableListOf<String>()
    ids.addAll(Files.readAllLines(disabledPluginsConfig))
    ids.remove(PLUGIN_ID)
    Files.writeString(disabledPluginsConfig, ids.joinToString("\n"))
  }
  catch (_: Throwable) {
  }
}

private const val NOT_MIGRATED_FILENAME = ".ai-migration-disabled"

@ApiStatus.Internal
fun isAIDisabledBeforeMigrated(): Boolean {
  return Files.exists(PathManager.getConfigDir().resolve(NOT_MIGRATED_FILENAME))
}

internal fun migrateAiFromUpdateCommand(newPluginsDir: Path,
                                        newConfigDir: Path,
                                        previousVersion: String?,
                                        log: Logger,
                                        pluginsToDownload: MutableList<IdeaPluginDescriptor>) {
  if (previousVersion == "2023.3") {
    val disabledPluginsConfig = newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
    val disabledIds: Set<PluginId> = if (Files.exists(disabledPluginsConfig)) {
      tryReadPluginIdsFromFile(disabledPluginsConfig, Logger.getInstance(AIAssistant241::class.java))
    }
    else {
      emptySet()
    }

    if (disabledIds.any { it.idString == PLUGIN_ID }) {
      createNotMigratedFile(newConfigDir)
      excludeFromDisabled(newConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME))

      val mlPluginDir = newPluginsDir.resolve("ml-llm")
      if (Files.exists(mlPluginDir)) {
        try {
          FileUtil.deleteRecursively(mlPluginDir)
        }
        catch (e: IOException) {
          log.info("Unable to remove $mlPluginDir", e)
        }
      }
    }
    else {
      pluginsToDownload.add(PluginNode(PluginId.getId(PLUGIN_ID)))
    }
  }
}