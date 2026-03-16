// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.ui

import com.intellij.DynamicBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE = "messages.XDebuggerUiBundle"

@ApiStatus.Internal
object XDebuggerUiBundle {
  private val instance = DynamicBundle(XDebuggerUiBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): @Nls String {
    return instance.getMessage(key, *params)
  }

  @JvmStatic
  fun lazyMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): Supplier<@Nls String> {
    return instance.getLazyMessage(key, *params)
  }
}
