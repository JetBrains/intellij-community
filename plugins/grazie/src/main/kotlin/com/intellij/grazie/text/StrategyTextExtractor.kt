@file:Suppress("DEPRECATION")

package com.intellij.grazie.text

import com.intellij.diagnostic.PluginException
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.openapi.diagnostic.logger
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
    return TextContent.joinWithWhitespace('\n', fragments)
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

    val stealthyRanges = strategy.getStealthyRanges(root, content)
    val filtered = stealthyRanges.filter { it.first <= it.last && it.last < content.length }
    if (filtered.size != stealthyRanges.size) {
      PluginException.logPluginError(
        logger<StrategyTextExtractor>(),
        "$strategy produced invalid stealthy ranges $stealthyRanges in a text of length ${content.length}",
        null,
        strategy.javaClass)
    }
    val sorted = filtered.sortedBy { it.first }
    for (i in 1 until sorted.size) {
      if (sorted[i - 1].last >= sorted[i].first) {
        PluginException.logPluginError(
          logger<StrategyTextExtractor>(),
          "$strategy produced intersecting stealthy ranges ${sorted[i - 1]} and ${sorted[i]}",
          null,
          strategy.javaClass)
        return null
      }
    }
    return content.excludeRanges(sorted.map { TextContent.Exclusion(it.first, it.last + 1, false) })
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