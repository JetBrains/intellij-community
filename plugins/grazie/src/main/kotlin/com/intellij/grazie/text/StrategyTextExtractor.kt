@file:Suppress("DEPRECATION")

package com.intellij.grazie.text

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType

internal class StrategyTextExtractor(private val strategy: GrammarCheckingStrategy) {
  fun extractText(roots: List<PsiElement>): TextContent? {
    val wsTokens = strategy.getWhiteSpaceTokens()
    val fragments = roots
      .filter { it !is PsiCompiledElement && !wsTokens.contains(it.elementType) }
      .mapNotNull { buildTextContent(it) }
      .mapNotNull { trimLeadingQuotesAndSpaces(it) }
    return TextContent.joinWithWhitespace(fragments)
  }

  private fun trimLeadingQuotesAndSpaces(content: TextContent): TextContent? {
    val text = content.toString()
    var trimPrefix = StrategyUtils.quotesOffset(text)
    var trimSuffix = text.length - trimPrefix
    require(trimPrefix <= trimSuffix)

    while (trimSuffix > trimPrefix && text[trimSuffix - 1].isWhitespace()) trimSuffix--
    while (trimSuffix > trimPrefix && text[trimPrefix].isWhitespace()) trimPrefix++

    if (trimSuffix <= trimPrefix) return null

    if (trimPrefix > 0 || trimSuffix < text.length) {
      return content.excludeRange(TextRange(trimSuffix, text.length)).excludeRange(TextRange(0, trimPrefix))
    }
    return content
  }

  private fun buildTextContent(root: PsiElement): TextContent? {
    val domain =
      convertDomain(strategy.getContextRootTextDomain(root))
      ?: throw IllegalArgumentException("Non-text ${root.javaClass} in ${strategy.javaClass}")

    fun getBehavior(element: PsiElement) = when {
      element !== root && strategy.isMyContextRoot(element) -> GrammarCheckingStrategy.ElementBehavior.ABSORB // absorbing nested context root
      else -> strategy.getElementBehavior(root, element)
    }

    val content = TextContentBuilder.FromPsi
                    .withUnknown { e -> getBehavior(e) == GrammarCheckingStrategy.ElementBehavior.ABSORB }
                    .excluding { e -> getBehavior(e) == GrammarCheckingStrategy.ElementBehavior.STEALTH }
                    .build(root, domain, TextRange(0, root.textLength)) ?: return null

    return TextContentBuilder.excludeRanges(content, strategy.getStealthyRanges(root, content))
  }

  internal companion object {
    @JvmStatic
    fun convertDomain(domain: GrammarCheckingStrategy.TextDomain): TextContent.TextDomain? {
      return when (domain) {
        GrammarCheckingStrategy.TextDomain.LITERALS -> TextContent.TextDomain.LITERALS
        GrammarCheckingStrategy.TextDomain.COMMENTS -> TextContent.TextDomain.COMMENTS
        GrammarCheckingStrategy.TextDomain.DOCS -> TextContent.TextDomain.DOCUMENTATION
        GrammarCheckingStrategy.TextDomain.PLAIN_TEXT -> TextContent.TextDomain.PLAIN_TEXT
        else -> null
      }
    }
  }
}