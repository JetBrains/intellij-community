// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.util.PropertiesComponent
import com.jetbrains.builtInHelp.settings.SettingsPage
import org.jetbrains.annotations.NonNls

class Utils {
  companion object {
    @NonNls
    private const val EMPTY_STRING = ""

    @NonNls
    const val BASE_HELP_URL = "https://www.jetbrains.com/"

    private val secureKey = CredentialAttributes("Web Help Bundle")

    @JvmStatic
    @NonNls
    fun loadSetting(key: SettingsPage.SettingKey, default: String): String {
      return PropertiesComponent.getInstance().getValue(key.first, default)
    }

    @JvmStatic
    fun saveSetting(key: SettingsPage.SettingKey, value: String?) {
      PropertiesComponent.getInstance().setValue(key.first, value, EMPTY_STRING)
    }
  }
}