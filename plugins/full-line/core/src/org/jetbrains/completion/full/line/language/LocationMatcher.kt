package org.jetbrains.completion.full.line.language

import com.intellij.codeInsight.completion.CompletionParameters

// Checks if location requires custom configuration
interface LocationMatcher {
  fun tryMatch(parameters: CompletionParameters): FullLineConfiguration?
}
