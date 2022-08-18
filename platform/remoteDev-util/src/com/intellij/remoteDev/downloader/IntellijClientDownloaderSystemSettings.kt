package com.intellij.remoteDev.downloader

import com.intellij.remoteDev.OsRegistryConfigProvider

object IntellijClientDownloaderSystemSettings {
  private val osRegistryConfigProvider = OsRegistryConfigProvider("JetBrainsClient")
  private const val downloadDestinationKey = "downloadDestination"

  fun getDownloadDestination(): OsRegistryConfigProvider.OsRegistrySystemSetting<String?> {
    val systemValue = osRegistryConfigProvider.get(downloadDestinationKey)
    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(systemValue.value, systemValue.osOriginLocation)
    }
    return OsRegistryConfigProvider.OsRegistrySystemSetting(null, null)
  }
}