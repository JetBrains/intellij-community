// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.repository.search.completion.lookup.DependencyCompletionFuzzyMatcher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GradleDependencyCompletionFuzzyMatcher(prefix: String) : DependencyCompletionFuzzyMatcher(prefix) {
  override fun cloneWithPrefix(prefix: String): PrefixMatcher = GradleDependencyCompletionFuzzyMatcher(prefix)
  override fun startOffset(searchResult: String): Int = searchResult.indexOf("(").coerceAtLeast(0)
}