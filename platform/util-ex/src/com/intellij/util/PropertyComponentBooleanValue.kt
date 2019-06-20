// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.ide.util.PropertiesComponent
import kotlin.reflect.KProperty

/**
 * Value to be used as a Kotlin delegate for non-roamable application-level settings.
 *
 * Example: `var myCoolSetting by PropertyComponentBooleanValue("my.cool.setting", true)`.
 *
 * @see PropertiesComponent
 */
class PropertyComponentBooleanValue(
  private val name: String,
  private val defaultValue: Boolean
) {

  operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
    return PropertiesComponent.getInstance().getBoolean(name, defaultValue)
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
    return PropertiesComponent.getInstance().setValue(name, value, defaultValue)
  }
}
