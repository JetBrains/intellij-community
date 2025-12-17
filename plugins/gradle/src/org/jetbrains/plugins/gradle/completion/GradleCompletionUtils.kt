// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.completion

import com.intellij.codeInsight.completion.CompletionUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun removeDummySuffix(value: String?): String {
  if (value == null) {
    return ""
  }
  val index = value.indexOf(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
  val result = if (index >= 0) {
    value.take(index)
  } else {
    value
  }
  return result.trim()
}
