// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BigDataToolsMigration")

package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil.compareVersionNumbers
import java.nio.file.Path

private const val BIG_DATA_TOOLS_PLUGIN_ID = "com.intellij.bigdatatools"

@Suppress("UNUSED_PARAMETER")
internal fun migratePlugins(currentProductVersion: String,
                            newConfigDir: Path,
                            oldConfigDir: Path,
                            pluginsToMigrate: MutableList<IdeaPluginDescriptor>,
                            pluginsToDownload: MutableList<IdeaPluginDescriptor>) {

  val currentPluginsToDownload = pluginsToDownload.map { it.pluginId.idString }.toSet()

  if (compareVersionNumbers(currentProductVersion, "232") >= 0
      && currentPluginsToDownload.contains(BIG_DATA_TOOLS_PLUGIN_ID)) {

    fun addPluginIfNeeded(pluginIdString: String) {
      if (!currentPluginsToDownload.contains(pluginIdString)) {
        pluginsToDownload.add(PluginNode(PluginId.getId(pluginIdString)))
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