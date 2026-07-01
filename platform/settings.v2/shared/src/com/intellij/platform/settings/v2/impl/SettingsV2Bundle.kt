// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE: String = "messages.SettingsV2Bundle"

object SettingsV2Bundle {
  private val INSTANCE = DynamicBundle(SettingsV2Bundle::class.java, BUNDLE)

  @Nls
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): String = INSTANCE.getMessage(key, *params)
}
