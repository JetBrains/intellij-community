// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.jetbrains.builtInHelp.settings.SettingsPage

class Utils {
  companion object {
    private const val EMPTY_STRING = ""
    const val BASE_HELP_URL = "https://www.jetbrains.com/"

    private val secureKey = CredentialAttributes("Web Help Bundle")

    @JvmStatic
    fun getStoredValue(key: SettingsPage.SettingKey, default: String): String {
      if (!key.second) {
        return PropertiesComponent.getInstance().getValue(key.first, default)
      }
      val passwordSafe = PasswordSafe.instance
      val credentials = passwordSafe.get(secureKey)
      if (credentials != null) return credentials.getPasswordAsString().toString()
      return EMPTY_STRING
    }

    @JvmStatic
    fun setStoredValue(key: SettingsPage.SettingKey, value: String?): Boolean {
      if (!key.second) {
        PropertiesComponent.getInstance().setValue(key.first, value, EMPTY_STRING)
        return true
      }
      val passwordSafe = PasswordSafe.instance
      passwordSafe.set(secureKey, Credentials(key.first, value))
      return true
    }
  }
}