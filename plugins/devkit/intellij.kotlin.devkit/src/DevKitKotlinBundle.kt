// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE_FQN: @NonNls String = "messages.DevKitKotlinBundle"

object DevKitKotlinBundle {

  private val BUNDLE = DynamicBundle(DevKitKotlinBundle::class.java, BUNDLE_FQN)

  fun message(
    @NonNls @PropertyKey(resourceBundle = BUNDLE_FQN) key: String,
    vararg params: Any,
  ): @Nls String = BUNDLE.getMessage(key, *params)

  fun messagePointer(
    @NonNls @PropertyKey(resourceBundle = BUNDLE_FQN) key: String,
    vararg params: Any,
  ): Supplier<@Nls String> = BUNDLE.getLazyMessage(key, *params)
}