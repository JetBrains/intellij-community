// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import com.intellij.openapi.util.TextRange
import java.util.regex.Matcher
import java.util.regex.Pattern

object Text {
  fun isNewline(char: Char) = char == '\n'

  private val PUNCTUATIONS: Set<Byte> = setOf(Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                                              Character.OTHER_PUNCTUATION, Character.CONNECTOR_PUNCTUATION,
                                              Character.DASH_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
                                              Character.FINAL_QUOTE_PUNCTUATION)

  fun isPunctuation(char: Char) = when (Character.getType(char).toByte()) {
    in PUNCTUATIONS -> true
    else -> false
  }

  fun isQuote(char: Char) = char == '\'' || char == '\"'

  @JvmStatic
  fun isSingleSentence(text: CharSequence) = !text.contains(Regex("\\.\\s"))

  @JvmStatic
  fun findParagraphRange(text: CharSequence, range: TextRange): TextRange {
    var start = range.startOffset
    while (start > 0) {
      var wsStart = start
      while (wsStart > 0 && text[wsStart - 1].isWhitespace()) wsStart--
      if (wsStart < start && text.subSequence(wsStart, start).count { it == '\n' } > 1) break
      start = wsStart - 1
    }

    var end = range.endOffset
    while (end < text.length) {
      var wsEnd = end
      while (wsEnd < text.length && text[wsEnd].isWhitespace()) wsEnd++
      if (wsEnd > end && text.subSequence(end, wsEnd).count { it == '\n' } > 1) break
      end = wsEnd + 1
    }

    return TextRange(start.coerceAtLeast(0), end.coerceAtMost(text.length))
  }

  private const val id = "[a-zA-Z_$][a-zA-Z0-9_$]*"
  private const val assignment = "= ['\"]"
  private const val idChain = "$id\\.$id\\.$id"
  private const val pyFString = "\\bf\""
  private const val call = "$id\\(.*\\)"
  private const val commonCharEscape = "\\\\[ntbru]"
  private const val keyColonValue = "\n *[a-zA-Z0-9_\$'\"-]+: *[a-zA-Z0-9_\$'\"-]+"

  private const val cSharpTypeOrValueTuple = "($id|\\($id(, *$id)+\\))"
  private const val cSharpGenericTypeParameter = "(((in|out) +)?$cSharpTypeOrValueTuple)"
  private const val cSharpGenericMethod = "$id\\<$cSharpGenericTypeParameter(, *$cSharpGenericTypeParameter)*\\>\\("
  private const val scalaTodo = "= ?\\?\\?\\?"
  private val codeLikePattern = Regex("$assignment|$idChain|$pyFString|$call|$commonCharEscape|$keyColonValue|$cSharpGenericMethod|$scalaTodo")

  private val javaVariable = Regex("\\b([A-Z])($id) ([a-z])(\\2) ?[;:,)=]")

  fun CharSequence.looksLikeCode(): Boolean {
    if (codeLikePattern.find(this) != null) return true

    val matcher = javaVariable.find(this)
    if (matcher != null && matcher.groups[1]!!.value == matcher.groups[3]!!.value.uppercase()) {
      return true
    }

    if (this.any { it.code > 127 }) return false

    var codeChars = 0
    var textTokens = 0
    var inToken = false
    for (c in this) {
      if (c.isLetterOrDigit()) {
        if (!inToken) {
          inToken = true
          textTokens++
        }
      } else {
        inToken = false
        if ("(){}[]<>=+-*/%|&!;,.:\\@$#^".contains(c)) {
          codeChars++
        }
      }
    }
    return codeChars > 0 && textTokens < codeChars
  }

  /** @return all non-intersecting occurrences of the given pattern in the given text */
  @JvmStatic
  fun allOccurrences(pattern: Pattern, text: CharSequence): List<TextRange> {
    var start = 0
    val result = arrayListOf<TextRange>()
    val matcher: Matcher = pattern.matcher(text)
    while (true) {
      if (!matcher.find(start)) break

      result.add(TextRange(matcher.start(), matcher.end()))
      start = matcher.end().coerceAtLeast(start + 1)
    }
    return result
  }
}
