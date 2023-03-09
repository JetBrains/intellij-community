package com.intellij.grazie.text

import com.intellij.grazie.utils.Text.looksLikeCode
import com.intellij.openapi.util.TextRange

internal class CodeProblemFilter : ProblemFilter() {

  override fun shouldIgnore(problem: TextProblem): Boolean {
    return problem.highlightRanges.any { textAround(problem.text, it).looksLikeCode() }
  }

  private fun textAround(text: CharSequence, range: TextRange): CharSequence {
    return text.subSequence((range.startOffset - 20).coerceAtLeast(0), (range.endOffset + 20).coerceAtMost(text.length))
  }
}