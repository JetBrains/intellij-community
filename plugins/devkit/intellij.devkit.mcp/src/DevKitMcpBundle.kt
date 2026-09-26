// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.mcp

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object DevKitMcpBundle {
  private const val BUNDLE: String = "messages.DevKitMcpBundle"
  private val INSTANCE: DynamicBundle = DynamicBundle(DevKitMcpBundle::class.java, BUNDLE)

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String = INSTANCE.getMessage(key, *params)
}
