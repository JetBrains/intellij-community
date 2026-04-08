package com.intellij.grazie.text

import ai.grazie.rules.code.CodeDetector
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.grazie.utils.treeRange

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
    if (!problem.shouldSuppressInCodeLikeFragments() || !shouldSuppressInText(problem.text)) return false
    val codeFragments = CodeDetector.findCodeFragments(problem.text)
    return problem.highlightRanges.any { range -> codeFragments.any { it.containsInclusive(range.treeRange()) } }
  }

  override fun shouldIgnoreTypo(problem: TextProblem): Boolean {
    return GrazieSpellCheckingInspection.hasSameNamedReferenceInFile(
      problem.highlightRanges.first().subSequence(problem.text).toString(), problem.text.commonParent
    )
  }

  protected open fun shouldSuppressInText(text: TextContent): Boolean =
    text.domain == TextContent.TextDomain.COMMENTS || text.domain == TextContent.TextDomain.LITERALS

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