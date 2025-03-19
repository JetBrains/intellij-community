// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.GitLabBundle"

object GitLabBundle : DynamicBundle(BUNDLE) {
  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) @NonNls String, vararg params: Any): @Nls String {
    return if (containsKey(key)) getMessage(key, *params) else GitLabDeprecatedMessagesBundle.message(key)
  }

  @JvmStatic
  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE) @NonNls String, vararg params: Any): Supplier<String> {
    return if (containsKey(key)) getLazyMessage(key, *params) else GitLabDeprecatedMessagesBundle.messagePointer(key, *params)
  }
}