// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.core

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE_FQN = "messages.DevCoreBundle"

internal object DevCoreBundle {
  private val BUNDLE = DynamicBundle(DevCoreBundle::class.java, BUNDLE_FQN)

  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE_FQN) key: String, vararg params: Any): String =
    BUNDLE.getMessage(key, *params)

  @JvmStatic
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE_FQN) key: String, vararg params: Any): Supplier<@Nls String> =
    BUNDLE.getLazyMessage(key, *params)
}
