// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import com.intellij.openapi.extensions.ExtensionPointName

interface ThirdPartyProductSettingsTransfer {

  companion object {
    val EP_NAME = ExtensionPointName<ThirdPartyProductSettingsTransfer>("com.intellij.transferSettings.thirdPartyProductSettingsTransfer")
  }

  fun getProviders(): List<TransferSettingsProvider>
}
