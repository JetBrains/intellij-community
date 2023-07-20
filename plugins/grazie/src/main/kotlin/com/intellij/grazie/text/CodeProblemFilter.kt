package com.intellij.grazie.text

import com.intellij.grazie.utils.Text.looksLikeCode
import com.intellij.openapi.util.TextRange

/**
 * A natural language problem filter that suppresses problems in areas which resemble code.
 * The reason: in some contexts (e.g. in plain text, comments), people often embed code samples into text without any markup.
 *
 * The default implementation suppresses problems in comments and literals.
 * Specific languages can also add subclass filters to their language
 * if code embedding is common in the corresponding domains in those languages.
 * @see InPlainText
 * @see InDocumentation
 */
open class CodeProblemFilter : ProblemFilter() {

  override fun shouldIgnore(problem: TextProblem): Boolean {
    return problem.shouldSuppressInCodeLikeFragments() &&
           shouldSuppressInText(problem.text) &&
           problem.highlightRanges.any { textAround(problem.text, it).looksLikeCode() }
  }

  protected open fun shouldSuppressInText(text: TextContent): Boolean =
    text.domain == TextContent.TextDomain.COMMENTS || text.domain == TextContent.TextDomain.LITERALS

  private fun textAround(text: CharSequence, range: TextRange): CharSequence {
    return text.subSequence((range.startOffset - 20).coerceAtLeast(0), (range.endOffset + 20).coerceAtMost(text.length))
  }

  /** A variant of [CodeProblemFilter] that suppresses problems in code-like fragments in [TextContent.TextDomain.PLAIN_TEXT] */
  class InPlainText: CodeProblemFilter() {
    override fun shouldSuppressInText(text: TextContent): Boolean {
      return text.domain == TextContent.TextDomain.PLAIN_TEXT
    }
  }

  /** A variant of [CodeProblemFilter] that suppresses problems in code-like fragments in [TextContent.TextDomain.DOCUMENTATION] */
  class InDocumentation: CodeProblemFilter() {
    override fun shouldSuppressInText(text: TextContent): Boolean {
      return text.domain == TextContent.TextDomain.DOCUMENTATION
    }
  }
}