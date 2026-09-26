// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.downloader

import com.intellij.remoteDev.OsRegistryConfigProvider

object IntellijClientDownloaderSystemSettings {
  private val osRegistryConfigProvider = OsRegistryConfigProvider("JetBrainsClient")
  private const val downloadDestinationKey = "downloadDestination"
  private const val versionManagementEnabledKey = "versionManagementEnabled"
  private const val modifiedDateInManifestIncludedKey = "modifiedDateInManifestIncluded"

  fun getDownloadDestination(): OsRegistryConfigProvider.OsRegistrySystemSetting<String?> {
    val systemValue = osRegistryConfigProvider.get(downloadDestinationKey)
    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(systemValue.value, systemValue.osOriginLocation)
    }
    return OsRegistryConfigProvider.OsRegistrySystemSetting(null, null)
  }

  fun isVersionManagementEnabled(): Boolean {
    return osRegistryConfigProvider.`is`(versionManagementEnabledKey, true)
  }

  fun isModifiedDateInManifestIncluded(): Boolean {
    return osRegistryConfigProvider.`is`(modifiedDateInManifestIncludedKey, true)
  }
}