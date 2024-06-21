// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.stacktrace

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object DevKitStackTraceBundle {
  private const val BUNDLE_FQN: @NonNls String = "messages.DevKitStackTraceBundle"
  private val BUNDLE = DynamicBundle(DevKitStackTraceBundle::class.java, BUNDLE_FQN)

  fun message(key: @PropertyKey(resourceBundle = BUNDLE_FQN) String, vararg params: Any): @Nls String = BUNDLE.getMessage(key, *params)
}
