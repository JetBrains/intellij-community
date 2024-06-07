// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.users

import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.SystemProperties

object LocalUserSettings {

  private const val userNameKey = "local.user.name"
  private const val userNameObsoleteKey = "code.with.me.user.name"

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
      return SystemProperties.getUserName()
    }
    set(value) {
      PropertiesComponent.getInstance().setValue(userNameKey, value)
    }

}
