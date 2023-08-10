package com.intellij.remoteDev

import java.net.URI

object RemoteDevSystemSettings {
  const val sectionName = "RemoteDev"

  private val osRegistryConfigProvider = OsRegistryConfigProvider(sectionName)

  private const val productCodePlaceholder = "<PRODUCT_CODE>"
  private const val productsInfoUrlKey = "productsInfoUrl"
  private const val clientDownloadUrlKey = "clientDownloadUrl"
  private const val jreDownloadUrlKey = "jreDownloadUrl"
  private const val pgpPublicKeyUrlKey = "pgpPublicKeyUrl"

  private fun defaultProductsUrl(productCode: String) = "https://data.services.jetbrains.com/products?code=$productCode"
  private const val defaultClientUrlLocation = "https://download.jetbrains.com/idea/code-with-me/"
  private const val defaultJreUrlLocation = "https://download.jetbrains.com/idea/jbr/"

  fun getProductsUrl(productCode: String): OsRegistryConfigProvider.OsRegistrySystemSetting<String> {
    val systemValue = osRegistryConfigProvider.get(productsInfoUrlKey)
    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(systemValue.value.replace(productCodePlaceholder, productCode), systemValue.osOriginLocation)
    }
    return OsRegistryConfigProvider.OsRegistrySystemSetting(defaultProductsUrl(productCode), null)
  }

  fun getPgpPublicKeyUrl(): OsRegistryConfigProvider.OsRegistrySystemSetting<String?> {
    val systemValue = osRegistryConfigProvider.get(pgpPublicKeyUrlKey)
    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(systemValue.value, systemValue.osOriginLocation)
    }
    return OsRegistryConfigProvider.OsRegistrySystemSetting(null, null)
  }

  fun getClientDownloadUrl(): OsRegistryConfigProvider.OsRegistrySystemSetting<URI> {
    val systemValue = osRegistryConfigProvider.get(clientDownloadUrlKey)
    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(URI(systemValue.value), systemValue.osOriginLocation)
    }
    return OsRegistryConfigProvider.OsRegistrySystemSetting(URI(defaultClientUrlLocation), null)
  }

  fun getJreDownloadUrl(): OsRegistryConfigProvider.OsRegistrySystemSetting<URI> {
    val systemValue = osRegistryConfigProvider.get(jreDownloadUrlKey)
    if (systemValue != null) {
      return OsRegistryConfigProvider.OsRegistrySystemSetting(URI(systemValue.value), systemValue.osOriginLocation)
    }
    return OsRegistryConfigProvider.OsRegistrySystemSetting(URI(defaultJreUrlLocation), null)
  }


}