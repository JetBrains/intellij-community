package com.intellij.grazie.cloud.license

import com.intellij.ui.JBAccountInfoService
import com.intellij.ui.JBAccountInfoService.LicenseListResult.LicenseList
import kotlinx.coroutines.future.await

private const val GrazieLicenseCode = "GZL"

internal sealed interface LicenseState {
  data object Empty : LicenseState

  data object NoLicense : LicenseState

  data class Valid(val data: LicenseKeyData) : LicenseState

  data object Invalid : LicenseState

  companion object {
    suspend fun obtain(): LicenseState {
      try {
        val jbaService = JBAccountInfoService.getInstance() ?: return Empty
        val licenses = jbaService.getAvailableLicenses(GrazieLicenseCode).await() ?: return NoLicense
        if (licenses !is LicenseList) return Empty

        // Iterate over all Grazie licenses and pick the most relevant one.
        // Order to check: `STANDARD` -> `TRIAL` -> `FREE`.
        for (kind in JBAccountInfoService.LicenseKind.entries) {
          for (license in licenses.licenses) {
            if (kind == license.licenseKind) {
              return Valid(data = LicenseKeyData(license.licenseId))
            }
          }
        }
        return NoLicense
      } catch (_: IllegalStateException) {
        // LicenseManager may not be installed in some IDE configurations
        return Empty
      }
    }
  }
}