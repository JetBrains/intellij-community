// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.users

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger

object LocalUserSettings {

  private const val userNameKey = "local.user.name"
  private const val userNameObsoleteKey = "code.with.me.user.name"
  private const val userNameDefault = "User"

  var userName: String
    get() {
      var value = PropertiesComponent.getInstance().getValue(userNameKey)
      if (value == null) {
        // Compatibility with old code-with-me key name
        value = PropertiesComponent.getInstance().getValue(userNameObsoleteKey)
        if (value != null) {
          PropertiesComponent.getInstance().setValue(userNameKey, value)
          PropertiesComponent.getInstance().setValue(userNameObsoleteKey, null)
        }
      }
      if (!value.isNullOrBlank()) return value
      return getDefaultSystemUserName()
    }
    set(value) {
      PropertiesComponent.getInstance().setValue(userNameKey, value)
    }

  fun getDefaultSystemUserName(): String {
    try {
      return System.getProperty("user.name")
    }
    catch (ex: Throwable) {
      Logger.getInstance(LocalUserSettings::class.java).error(ex)
    }

    return userNameDefault
  }
}