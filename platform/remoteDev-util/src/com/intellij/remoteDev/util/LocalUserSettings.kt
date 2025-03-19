// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.util

import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.SystemProperties

object LocalUserSettings {
  private const val USER_NAME_KEY = "local.user.name"

  var userName: String
    get() = PropertiesComponent.getInstance().getValue(USER_NAME_KEY).takeIf { !it.isNullOrBlank() } ?: SystemProperties.getUserName()
    set(value) = PropertiesComponent.getInstance().setValue(USER_NAME_KEY, value)
}
