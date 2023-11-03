// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.openapi.util.text.StringUtil

class HttpClientPostmanConverter233 : PluginMigration() {
  private val POSTMAN_CONVERTER_ID = "com.intellij.restClient.postmanConverter"

  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (StringUtil.compareVersionNumbers(descriptor.options.currentProductVersion, "233") >= 0 &&
        descriptor.currentPluginsToMigrate.contains(POSTMAN_CONVERTER_ID)
    ) {
      descriptor.removePlugin(POSTMAN_CONVERTER_ID)
    }
  }
}