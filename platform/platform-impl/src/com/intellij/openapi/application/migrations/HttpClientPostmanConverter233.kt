// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

class HttpClientPostmanConverter233 : PluginMigration() {
  private val POSTMAN_CONVERTER_ID = "com.intellij.restClient.postmanConverter"

  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (descriptor.currentPluginsToMigrate.contains(POSTMAN_CONVERTER_ID)) {
      descriptor.removePlugin(POSTMAN_CONVERTER_ID)
    }
  }
}