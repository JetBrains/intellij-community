// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.util.text.StringUtil

internal class PythonProMigration242 : PluginMigration() {
  private val PYTHON_PLUGIN_CORE = "PythonCore"
  private val PYTHON_PLUGIN_PRO = "Pythonid"

  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (StringUtil.compareVersionNumbers(descriptor.options.currentProductVersion, "242") >= 0 &&
        (descriptor.currentPluginsToMigrate.contains(PYTHON_PLUGIN_PRO) ||
         descriptor.currentPluginsToDownload.contains(PYTHON_PLUGIN_PRO)))
    {
      descriptor.addPluginIfNeeded(PYTHON_PLUGIN_CORE)
    }
  }
}