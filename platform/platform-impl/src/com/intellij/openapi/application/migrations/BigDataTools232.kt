// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil.compareVersionNumbers

class BigDataTools232 {
  private val BIG_DATA_TOOLS_PLUGIN_ID = "com.intellij.bigdatatools"

  fun migratePlugins(options: PluginMigrationOptions) {
    val currentPluginsToDownload = options.pluginsToDownload.map { it.pluginId.idString }.toSet()

    if (compareVersionNumbers(options.currentProductVersion, "232") >= 0
        && currentPluginsToDownload.contains(BIG_DATA_TOOLS_PLUGIN_ID)) {

      fun addPluginIfNeeded(pluginIdString: String) {
        if (!currentPluginsToDownload.contains(pluginIdString)) {
          options.pluginsToDownload.add(PluginNode(PluginId.getId(pluginIdString)))
        }
      }

      addPluginIfNeeded("com.intellij.bigdatatools.core")
      addPluginIfNeeded("com.intellij.bigdatatools.kafka")
      addPluginIfNeeded("com.intellij.bigdatatools.rfs")
      addPluginIfNeeded("com.intellij.bigdatatools.zeppelin")
      addPluginIfNeeded("com.intellij.bigdatatools.metastore.core")
      addPluginIfNeeded("com.intellij.bigdatatools.spark")
      addPluginIfNeeded("com.intellij.bigdatatools.flink")
      addPluginIfNeeded("com.intellij.bigdatatools.binary.files")
    }
  }
}