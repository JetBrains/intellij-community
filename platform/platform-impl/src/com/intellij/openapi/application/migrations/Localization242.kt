// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.loadDescriptorsFromCustomPluginDir
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path

internal fun enableL10nIfPluginInstalled(previousVersion: String?, oldPluginsDir: Path) {
  if (previousVersion == null || StringUtil.compareVersionNumbers(previousVersion, "2024.2") != -1) return


  val loadedDescriptors = loadDescriptorsFromCustomPluginDir(oldPluginsDir, true)
  val bundledL10nPluginsIds = LocalizationUtil.l10nPluginIdToLanguageTag.keys

  loadedDescriptors.enabledPluginsById.keys
    .firstOrNull { bundledL10nPluginsIds.contains(it.idString) }
    ?.let {
      val languageTag = LocalizationUtil.l10nPluginIdToLanguageTag[it.idString]!!
      EarlyAccessRegistryManager.setAndFlush(mapOf("i18n.locale" to languageTag))
    }
}