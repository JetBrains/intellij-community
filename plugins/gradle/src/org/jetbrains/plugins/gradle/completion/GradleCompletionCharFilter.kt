// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.completion

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * GradleCompletionCharFilter is needed to avoid hiding
 * the completions after typing special characters
 * like '-' or ':', which appear in dependencies.
 */
private class GradleCompletionCharFilter : CharFilter() {
  private val acceptableChars = setOf('-', ':', '.')

  override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? {
    val isDependencyCompletion = lookup.currentItem?.getUserData(GRADLE_DEPENDENCY_COMPLETION) ?: return null
    if (isDependencyCompletion && c in acceptableChars) {
      return Result.ADD_TO_PREFIX
    }
    return null
  }
}

@ApiStatus.Internal
val GRADLE_DEPENDENCY_COMPLETION: Key<Boolean> = Key.create("GRADLE_DEPENDENCY_COMPLETION")