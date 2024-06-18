// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.loadDescriptorsFromOtherIde
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path

internal fun enableL10nIfPluginInstalled(previousVersion: String?, oldPluginsDir: Path) {
  if (previousVersion == null || StringUtil.compareVersionNumbers(previousVersion, "2024.2") != -1) return
  val loadedDescriptors = loadDescriptorsFromOtherIde(oldPluginsDir, null, null, null)
  val bundledL10nPluginsIds = LocalizationUtil.l10nPluginIdToLanguageTag.keys
  val l10nPluginId = loadedDescriptors.getIdMap().keys.firstOrNull { bundledL10nPluginsIds.contains(it.idString) }
  if (l10nPluginId != null) {
    val languageTag = LocalizationUtil.l10nPluginIdToLanguageTag[l10nPluginId.idString]!!
    EarlyAccessRegistryManager.setAndFlush(mapOf("i18n.locale" to languageTag))
  }
}