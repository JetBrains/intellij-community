// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

/**
 * Internal: plugins may not reuse platform i18n messages.
 */
@ApiStatus.Internal
object PlatformTerminalBundle {
  private const val BUNDLE: @NonNls String = "messages.PlatformTerminalBundle"
  private val INSTANCE = DynamicBundle(PlatformTerminalBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, *params)
  }

  @JvmStatic
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): Supplier<@Nls String> {
    return INSTANCE.getLazyMessage(key, *params)
  }
}
