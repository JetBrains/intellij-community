// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE_FQN = "messages.DevPluginLoadingBundle"

internal object DevPluginLoadingBundle {
  private val bundle = DynamicBundle(DevPluginLoadingBundle::class.java, BUNDLE_FQN)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE_FQN) key: String, vararg params: Any): String =
    bundle.getMessage(key, *params)
}
