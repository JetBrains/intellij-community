// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

private const val JUPYTER_PLUGIN_ID = "intellij.jupyter"
private const val JUPYTER_PY_PLUGIN_ID = "intellij.jupyter.py"

private const val KOTLIN_NOTEBOOKS_PLUGIN_ID = "org.jetbrains.plugins.kotlin.jupyter"
private const val R_PLUGIN_ID = "R4Intellij"
private const val NOTEBOOKS_CORE_PLUGIN_ID = "com.intellij.notebooks.core"

internal class NotebooksMigration242 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (descriptor.currentPluginsToMigrate.contains(JUPYTER_PY_PLUGIN_ID)) {
      descriptor.removePlugin(JUPYTER_PY_PLUGIN_ID)
      descriptor.removePluginToDownload(JUPYTER_PY_PLUGIN_ID)
    }

    val pluginsThatNeedNotebooksCore = listOf(
      KOTLIN_NOTEBOOKS_PLUGIN_ID,
      JUPYTER_PLUGIN_ID,
      R_PLUGIN_ID,
    )

    if (pluginsThatNeedNotebooksCore.any { pluginId ->
        descriptor.currentPluginsToMigrate.contains(pluginId)
        || descriptor.currentPluginsToDownload.contains(pluginId)
      }) {
      descriptor.addPluginIfNeeded(NOTEBOOKS_CORE_PLUGIN_ID)
    }
  }
}
