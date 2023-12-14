// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ide.plugins.PluginManagerCore

class RustUltimate241: PluginMigration() {
  private val OLD_RUST_PLUGIN = "org.rust.lang"
  private val NEW_RUST_PLUGIN = "com.jetbrains.rust"
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (StringUtil.compareVersionNumbers(descriptor.options.currentProductVersion, "241") >= 0 &&
        (descriptor.currentPluginsToMigrate.contains(OLD_RUST_PLUGIN) || descriptor.currentPluginsToDownload.contains(OLD_RUST_PLUGIN))) {
      descriptor.removePlugin(OLD_RUST_PLUGIN)
      descriptor.removePluginToDownload(OLD_RUST_PLUGIN)
      descriptor.addPluginIfNeeded(NEW_RUST_PLUGIN)
    }
  }

  override fun migratePlugins(options: PluginMigrationOptions) {
    if (PluginManagerCore.buildNumber.productCode != "IU") return
    super.migratePlugins(options)
  }
}