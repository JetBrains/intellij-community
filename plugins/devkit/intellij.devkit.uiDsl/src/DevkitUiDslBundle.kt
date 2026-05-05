// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_FQN: @NonNls String = "messages.DevkitUiDslBundle"

object DevkitUiDslBundle {

  private val BUNDLE = DynamicBundle(DevkitUiDslBundle::class.java, BUNDLE_FQN)

  fun message(
    @NonNls @PropertyKey(resourceBundle = BUNDLE_FQN) key: String,
    vararg params: Any,
  ) = BUNDLE.getMessage(key, *params)

}
