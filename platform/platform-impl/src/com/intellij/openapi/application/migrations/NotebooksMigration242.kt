// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.PluginManagerCore

private const val JUPYTER_PY_PLUGIN_ID = "intellij.jupyter.py"

private const val KOTLIN_NOTEBOOKS_PLUGIN_ID = "org.jetbrains.plugins.kotlin.jupyter"
private const val NOTEBOOKS_CORE_PLUGIN_ID = "com.intellij.notebooks.core"

internal class NotebooksMigration242 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (PluginManagerCore.buildNumber.productCode == "IU") {
      if (descriptor.currentPluginsToMigrate.contains(JUPYTER_PY_PLUGIN_ID)) {
        descriptor.removePlugin(JUPYTER_PY_PLUGIN_ID)
        descriptor.removePluginToDownload(JUPYTER_PY_PLUGIN_ID)
      }

      if (descriptor.currentPluginsToMigrate.contains(KOTLIN_NOTEBOOKS_PLUGIN_ID)
          || descriptor.currentPluginsToDownload.contains(KOTLIN_NOTEBOOKS_PLUGIN_ID)) {
        descriptor.addPluginIfNeeded(NOTEBOOKS_CORE_PLUGIN_ID)
      }
    }
  }
}
