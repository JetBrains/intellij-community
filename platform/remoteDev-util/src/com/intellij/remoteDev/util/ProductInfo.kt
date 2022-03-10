package com.intellij.remoteDev.util

import com.google.gson.GsonBuilder


// NOTE: add more fields if needed
//{
// "buildNumber" : "221.4501.155",
// "productCode" : "IU",
//  "version" : "2022.1",
//  "versionSuffix" : "EAP"
//}
data class ProductInfo(
  val buildNumber: String,
  val productCode: String,
  val version: String,
  val versionSuffix: String?,
) {
  companion object {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun fromJson(json: String): ProductInfo {
      return gson.fromJson(json, ProductInfo::class.java)
    }
  }
}