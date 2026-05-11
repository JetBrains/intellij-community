// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.android

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object ComposeIdeAndroidBundle {

  private const val BUNDLE: String = "messages.ComposeIdeAndroidBundle"

  private val INSTANCE: DynamicBundle = DynamicBundle(ComposeIdeAndroidBundle::class.java, BUNDLE)

  @Nls
  fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    INSTANCE.getMessage(key, *params)
}
