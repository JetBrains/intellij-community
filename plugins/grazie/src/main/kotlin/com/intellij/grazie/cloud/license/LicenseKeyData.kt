package com.intellij.grazie.cloud.license

private const val KeyPrefix = "key:"

internal data class LicenseKeyData(val id: String) {
  companion object {
    fun parse(key: String): LicenseKeyData? {
      val id = obtainLicenseId(key) ?: return null
      return LicenseKeyData(id)
    }

    private fun obtainLicenseId(key: String): String? {
      check(key.startsWith(KeyPrefix)) { "License key must start with $KeyPrefix" }
      val licenseParts = key.removePrefix(KeyPrefix).split("-").toTypedArray()
      if (licenseParts.size != 4) {
        return null
      }
      val (id, _, _, _) = licenseParts
      return id
    }
  }
}
