// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object ProxyOverrideSettings {
  var isOverrideEnabled: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(IS_OVERRIDE_ENABLED, DEFAULT)
    set(value) = PropertiesComponent.getInstance().setValue(IS_OVERRIDE_ENABLED, value, DEFAULT)

  private const val IS_OVERRIDE_ENABLED = "intellij.platform.proxy.override.enabled"
  private const val DEFAULT = true
}