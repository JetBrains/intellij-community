// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.strategy

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy.TextDomain
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.utils.LinkedSet
import com.intellij.grazie.utils.Text
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType


object StrategyUtils {
  private val EMPTY_LINKED_SET = LinkedSet<Nothing>()

  @Suppress("UNCHECKED_CAST")
  internal fun <T> emptyLinkedSet(): LinkedSet<T> = EMPTY_LINKED_SET as LinkedSet<T>

  /**
   * Get extension point of [strategy]
   *
   * @return extension point
   */
  internal fun getStrategyExtensionPoint(strategy: GrammarCheckingStrategy): LanguageExtensionPoint<GrammarCheckingStrategy> {
    return LanguageGrammarChecking.getExtensionPointByStrategy(strategy) ?: error("Strategy is not registered")
  }

  internal fun getTextDomainOrDefault(strategy: GrammarCheckingStrategy, root: PsiElement, default: TextDomain): TextDomain {
    val extension = getStrategyExtensionPoint(strategy)
    if (extension.language != root.language.id) return default

    val parser = LanguageParserDefinitions.INSTANCE.forLanguage(root.language) ?: return default
    return when {
      parser.stringLiteralElements.contains(root.elementType) -> TextDomain.LITERALS
      parser.commentTokens.contains(root.elementType) -> TextDomain.COMMENTS
      else -> default
    }
  }

  /**
   * Delete leading and trailing quotes with spaces
   *
   * @return deleted leading offset
   */
  internal fun trimLeadingQuotesAndSpaces(str: StringBuilder): Int = with(str) {
    var offset = quotesOffset(this)

    setLength(length - offset) // remove closing quotes and whitespaces
    while (isNotEmpty() && get(length - 1).isWhitespace()) deleteCharAt(length - 1)

    while (offset < length && get(offset).isWhitespace()) offset++
    repeat(offset) { deleteCharAt(0) } // remove opening quotes and whitespace

    return offset
  }

  /**
   * Convert double spaces into one after removing absorb/stealth elements
   *
   * @param position position in StringBuilder
   * @return true if deleted
   */
  internal fun deleteRedundantSpace(str: StringBuilder, position: Int): Boolean = with(str) {
    if (position in 1 until length) {
      if (get(position - 1) == ' ' && (Text.isPunctuation(get(position)) || get(position) == ' ')) {
        deleteCharAt(position - 1)
        return true
      }
    }

    return false
  }

  /**
   * Finds indent indexes for each line (indent of specific [chars])
   * NOTE: If you use this method in [GrammarCheckingStrategy.getStealthyRanges],
   * make sure that [GrammarCheckingStrategy.getReplaceCharRules] doesn't contain a [ReplaceNewLines] rule!
   *
   * @param str source text
   * @param chars characters, which considered as indentation
   * @return list of IntRanges for such indents
   */
  fun indentIndexes(str: CharSequence, chars: Set<Char>): LinkedSet<IntRange> {
    val result = LinkedSet<IntRange>()
    var from = -1
    for ((index, char) in str.withIndex()) {
      if ((Text.isNewline(char) || (index == 0 && char in chars)) && from == -1) {
        // for first line without \n
        from = index + if (Text.isNewline(char)) 1 else 0
      }
      else {
        if (from != -1) {
          if (char !in chars) {
            if (index > from) result.add(IntRange(from, index - 1))
            from = if (Text.isNewline(char)) index + 1 else -1
          }
        }
      }
    }

    if (from != -1) result.add(IntRange(from, str.length - 1))

    return result
  }

  /**
   * Get all siblings of [element] of specific [types]
   * which are no further than one line
   *
   * @param element element whose siblings are to be found
   * @param types possible types of siblings
   * @return sequence of siblings with whitespace tokens
   */
  fun getNotSoDistantSiblingsOfTypes(strategy: GrammarCheckingStrategy, element: PsiElement, types: Set<IElementType>) =
    getNotSoDistantSiblingsOfTypes(strategy, element) { type -> type in types }

  /**
   * Get all siblings of [element] of type accepted by [checkType]
   * which are no further than one line
   *
   * @param element element whose siblings are to be found
   * @param checkType predicate to check if type is accepted
   * @return sequence of siblings with whitespace tokens
   */
  fun getNotSoDistantSiblingsOfTypes(strategy: GrammarCheckingStrategy, element: PsiElement, checkType: (IElementType?) -> Boolean) =
    getNotSoDistantSimilarSiblings(strategy, element) { sibling -> checkType(sibling.elementType) }

  /**
   * Get all siblings of [element] of same type and text domain accepted by [checkSibling]
   * which are no further than one line
   *
   * @param element element whose siblings are to be found
   * @param checkSibling predicate to check if sibling is accepted
   * @return sequence of siblings with whitespace tokens
   */
  fun getNotSoDistantSimilarSiblings(strategy: GrammarCheckingStrategy, element: PsiElement, checkSibling: (PsiElement?) -> Boolean) =
    sequence {
    fun PsiElement.process(checkSibling: (PsiElement?) -> Boolean, next: Boolean) = sequence<PsiElement> {
      val whitespaceTokens = strategy.getWhiteSpaceTokens()
      var newLinesBetweenSiblingsCount = 0

      var sibling: PsiElement? = this@process
      while (sibling != null) {
        val candidate = if (next) sibling.nextSibling else sibling.prevSibling
        val type = candidate.elementType
        sibling = when {
          checkSibling(candidate) -> {
            newLinesBetweenSiblingsCount = 0
            candidate
          }
          type in whitespaceTokens -> {
            newLinesBetweenSiblingsCount += candidate.text.count { char -> char == '\n' }
            if (newLinesBetweenSiblingsCount > 1) null else candidate
          }
          else -> null
        }

        if (sibling != null) yield(sibling)
      }
    }

    yieldAll(element.process(checkSibling, false).toList().asReversed())
    yield(element)
    yieldAll(element.process(checkSibling, true))
  }

  private fun quotesOffset(str: CharSequence): Int {
    var index = 0
    while (index < str.length / 2) {
      if (str[index] != str[str.length - index - 1] || !Text.isQuote(str[index])) {
        return index
      }
      index++
    }

    return index
  }
}
