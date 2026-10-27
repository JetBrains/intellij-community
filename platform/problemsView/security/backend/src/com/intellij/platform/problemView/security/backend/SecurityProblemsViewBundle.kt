// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.security.backend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.SecurityProblemsViewBundle"

internal object SecurityProblemsViewBundle {
  private val bundle = DynamicBundle(SecurityProblemsViewBundle::class.java, BUNDLE)

  @Nls
  fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): String = bundle.getMessage(key, *params)
}
