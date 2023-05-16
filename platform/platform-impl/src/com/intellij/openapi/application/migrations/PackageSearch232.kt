// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil.compareVersionNumbers

class PackageSearch232 {
  private val PACKAGE_SEARCH_PLUGIN_ID = "com.jetbrains.packagesearch.intellij-plugin"

  fun migratePlugins(options: PluginMigrationOptions) {
    val oldConfigDir = ConfigImportHelper.getNameWithVersion(options.oldConfigDir)
    if (oldConfigDir != "IdeaIC2023.1" && oldConfigDir != "IntelliJIdea2023.1") return

    if (compareVersionNumbers(options.currentProductVersion, "232") >= 0) {
      options.pluginsToDownload.add(PluginNode(PluginId.getId(PACKAGE_SEARCH_PLUGIN_ID)))
    }
  }
}