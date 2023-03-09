// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.GitLabBundle"

object GitLabBundle : DynamicBundle(BUNDLE) {
  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) @NonNls key: String, vararg params: Any): String = getMessage(key, *params)
}