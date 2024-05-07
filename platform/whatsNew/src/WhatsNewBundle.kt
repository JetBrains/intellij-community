// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.whatsNew

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

object WhatsNewBundle  {
  private const val pathToBundle = "messages.WhatsNewBundle"
  private val bundle by lazy { DynamicBundle(WhatsNewBundle::class.java, pathToBundle); }

  @Nls
  fun message(
    @PropertyKey(resourceBundle = pathToBundle) key: String,
    vararg params: Any
  ): String {
    return bundle.getMessage(key, *params)
  }
}