// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object GithubAIBundle {
  private const val BUNDLE: @NonNls String = "messages.GithubAIBundle"
  private val INSTANCE: DynamicBundle = DynamicBundle(GithubAIBundle::class.java, BUNDLE)

  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): @Nls String {
    return INSTANCE.getMessage(key, params)
  }

  @NotNull
  fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> {
    return INSTANCE.getLazyMessage(key, params)
  }
}