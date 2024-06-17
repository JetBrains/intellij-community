// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.gdpr.Version
import com.intellij.ide.plugins.loadDescriptorsFromOtherIde
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import java.nio.file.Path

internal fun enableL10nIfPluginInstalled(previousVersion: String?, oldPluginsDir: Path, bundledPluginPath: Path?, brokenPluginVersions: Map<PluginId, Set<String>>?, compatibleBuildNumber: BuildNumber?) {
  if (previousVersion == null || Version.fromString(previousVersion) >= Version.fromString("2024.2")) return
  val loadedDescriptors = loadDescriptorsFromOtherIde(oldPluginsDir, bundledPluginPath, brokenPluginVersions, compatibleBuildNumber)
  val bundledL10nPluginsIds = LocalizationUtil.l10nPluginIdToLanguageTag.keys
  val l10nPluginId = loadedDescriptors.getIdMap().keys.firstOrNull { bundledL10nPluginsIds.contains(it.idString) }
  if (l10nPluginId != null) {
    val languageTag = LocalizationUtil.l10nPluginIdToLanguageTag[l10nPluginId.idString]!!
    System.setProperty(LocalizationUtil.LOCALIZATION_KEY, languageTag)
  }
}
