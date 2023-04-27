package com.intellij.remoteDev.util

import com.google.gson.GsonBuilder
import com.intellij.openapi.util.NlsSafe
import java.util.*


// NOTE: add more fields if needed
//{
// "buildNumber" : "221.4501.155",
// "productCode" : "IU",
// "version" : "2022.1",
// "versionSuffix" : "EAP"
//}
data class ProductInfo(
  val buildNumber: @NlsSafe String,
  val productCode: @NlsSafe String,
  val version: @NlsSafe String,
  val versionSuffix: @NlsSafe String?,
) {
  companion object {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun fromJson(json: String): ProductInfo {
      return gson.fromJson(json, ProductInfo::class.java)
    }
  }

  fun presentableVersion(): @NlsSafe String {
    return version + fixVersionSuffix(versionSuffix)
  }

  private fun fixVersionSuffix(versionSuffix: String?): String {
    if (versionSuffix.isNullOrBlank()) {
      return ""
    }
    // we skip 'Release' word in human readable version suffix
    if (versionSuffix.lowercase(Locale.getDefault()).contains("release")) {
      return ""
    }

    // EAP, RC
    return " " + versionSuffix.trim()
  }
}