package com.intellij.remoteDev.downloader

import com.intellij.remoteDev.OsRegistryConfigProvider

object IntellijClientDownloaderSystemSettings {
  private val osRegistryConfigProvider = OsRegistryConfigProvider("JetBrainsClient")
  private const val downloadDestinationKey = "downloadDestination"
  private const val versionManagementEnabledKey = "versionManagementEnabled"

  fun getDownloadDestination(): OsRegistryConfigProvider.OsRegistrySystemSetting<String?> {
    val systemValue = osRegistryConfigProvider.get(downloadDestinationKey)
    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(systemValue.value, systemValue.osOriginLocation)
    }
    return OsRegistryConfigProvider.OsRegistrySystemSetting(null, null)
  }

  fun isVersionManagementEnabled(): OsRegistryConfigProvider.OsRegistrySystemSetting<Boolean> {
    val systemValue = osRegistryConfigProvider.get(versionManagementEnabledKey)

    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(systemValue.value.toBoolean(), systemValue.osOriginLocation)
    }

    return OsRegistryConfigProvider.OsRegistrySystemSetting(true, null)
  }
}