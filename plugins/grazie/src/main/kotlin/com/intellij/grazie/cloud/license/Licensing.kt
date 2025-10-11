package com.intellij.grazie.cloud.license

import com.intellij.ui.LicensingFacade

internal const val GrazieLicenseCode = "GZL"

internal sealed interface LicenseState {
  data object Empty: LicenseState

  data object NoLicense: LicenseState

  data class Valid(val data: LicenseKeyData): LicenseState

  data object Invalid: LicenseState

  companion object {
    fun obtain(): LicenseState {
      val facade = LicensingFacade.getInstance() ?: return Empty
      val stamp = facade.getConfirmationStamp(GrazieLicenseCode) ?: return NoLicense
      return when (val data = LicenseKeyData.Companion.parse(stamp)) {
        null -> Invalid
        else -> Valid(data = data)
      }
    }
  }
}