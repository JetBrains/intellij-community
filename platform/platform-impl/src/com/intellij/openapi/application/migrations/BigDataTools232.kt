// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil.compareVersionNumbers

class BigDataTools232: PluginMigration() {
  private val BIG_DATA_TOOLS_PLUGIN_ID = "com.intellij.bigdatatools"

  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    val options = descriptor.options
    if (compareVersionNumbers(options.currentProductVersion, "232") >= 0
        && descriptor.currentPluginsToDownload.contains(BIG_DATA_TOOLS_PLUGIN_ID)) {

      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.core")
      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.kafka")
      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.rfs")
      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.zeppelin")
      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.metastore.core")
      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.spark")
      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.flink")
      descriptor.addPluginIfNeeded("com.intellij.bigdatatools.binary.files")
    }
  }
}