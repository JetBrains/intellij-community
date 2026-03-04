// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy

import com.intellij.util.fuzzyMatching.GotoFileFuzzyMatcher
import com.intellij.util.fuzzyMatching.GotoFileFuzzyMatcherFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SmithWatermanMatcherFactory : GotoFileFuzzyMatcherFactory {
  override fun createMatcher(pattern: String): GotoFileFuzzyMatcher? {
    if (pattern.isBlank()) return null
    return SmithWatermanMatcher(pattern)
  }
}
