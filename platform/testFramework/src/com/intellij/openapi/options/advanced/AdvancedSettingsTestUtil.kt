// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.advanced

inline fun withAdvancedSettingValue(settingId: String, tempValue: Boolean, crossinline block: () -> Unit) {
  val currentValue = AdvancedSettings.getBoolean(settingId)
  try {
    AdvancedSettings.setBoolean(settingId, tempValue)
    block()
  }
  finally {
    AdvancedSettings.setBoolean(settingId, currentValue)
  }
}
