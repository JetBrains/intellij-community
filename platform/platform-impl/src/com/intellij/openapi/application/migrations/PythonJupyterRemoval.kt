// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.util.text.StringUtil

private const val JUPYTER_PY_PLUGIN_ID = "intellij.jupyter.py"

internal class PythonJupyterRemoval : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (
      StringUtil.compareVersionNumbers(descriptor.options.currentProductVersion, "242") >= 0 &&
      descriptor.currentPluginsToMigrate.contains(JUPYTER_PY_PLUGIN_ID)
    ) {
      descriptor.removePlugin(JUPYTER_PY_PLUGIN_ID)
      descriptor.removePluginToDownload(JUPYTER_PY_PLUGIN_ID)
    }
  }
}
