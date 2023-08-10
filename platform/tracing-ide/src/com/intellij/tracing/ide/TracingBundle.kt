// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tracing.ide

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

internal class TracingBundle : DynamicBundle(BUNDLE) {
  companion object {
    @NonNls
    private const val BUNDLE = "messages.TracingBundle"

    @JvmStatic
    private val INSTANCE: TracingBundle = TracingBundle()

    @JvmStatic
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
      return INSTANCE.getMessage(key, *params)
    }
  }
}