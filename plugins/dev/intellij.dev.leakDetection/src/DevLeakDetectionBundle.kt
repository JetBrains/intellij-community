// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE_FQN = "messages.DevLeakDetectionBundle"

internal object DevLeakDetectionBundle {
  private val BUNDLE = DynamicBundle(DevLeakDetectionBundle::class.java, BUNDLE_FQN)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE_FQN) key: String, vararg params: Any): String =
    BUNDLE.getMessage(key, *params)

  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE_FQN) key: String, vararg params: Any): Supplier<@Nls String> =
    BUNDLE.getLazyMessage(key, *params)
}
