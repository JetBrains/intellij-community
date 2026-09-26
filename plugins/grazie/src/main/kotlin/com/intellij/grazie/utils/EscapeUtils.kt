package com.intellij.grazie.utils

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.grazie.text.TextContent
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

/**
 * Replaces backslash-escaped characters in the current [TextContent] object with their corresponding characters
 * and marks problematic ranges or characters that cannot be resolved properly as [TextContent.ExclusionKind.unknown].
 *
 * This includes handling sequences like `\n` and `\t` by translating them into their whitespace character equivalents.
 * It also tracks offsets to identify and exclude invalid or unresolvable ranges in the text.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun TextContent.replaceBackslashEscapes(): TextContent {
  val text = this.replaceBackslashEscapedWhitespace()
  val offsets = IntArray(text.length + 1)
  CodeInsightUtilCore.parseStringCharacters(text.toString(), offsets)
  val exclusions = (1 until offsets.size).asSequence()
    .filter { i -> offsets[i] != 0 && offsets[i] - offsets[i - 1] != 1 }
    .map { index -> TextRange(offsets[index - 1], offsets[index]) }
    .map(TextContent.Exclusion::markUnknown)
    .toList()
  return text.excludeRanges(exclusions)
}

/**
 * Same as [replaceBackslashEscapedWhitespace], but escapes only `\n` and `\t`.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun TextContent.replaceBackslashEscapedWhitespace(): TextContent {
  return this.replaceBackslashEscapedWhitespace('n').replaceBackslashEscapedWhitespace('t')
}

/**
 * Same as [replaceBackslashEscapedWhitespace], but escapes only vertical tab (`\v`).
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun TextContent.replaceEscapedVerticalTab(): TextContent {
  return this.replaceBackslashEscapedWhitespace('v')
}

/**
 * Replaces occurrences of backslash-escaped whitespace-like characters in the current [TextContent] object
 * with their respective normalized whitespace representations.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun TextContent.replaceBackslashEscapedWhitespace(separator: Char): TextContent {
  val excluded = getBackslashExcludeRanges(this, separator)
  if (excluded.isEmpty()) return this

  val components = invertExcludedToContentRanges(excluded, this.length)
    .mapNotNull { this.subText(it) }
  return when (components.size) {
    0 -> this
    1 -> components.first()
    else -> TextContent.joinWithWhitespace(mapSeparator(separator), components)!!
  }
}

private fun getBackslashExcludeRanges(content: TextContent, symbol: Char): List<TextRange> {
  if (content.length < 2) return emptyList()
  val ranges = ArrayList<TextRange>()
  var i = 0
  while (i < content.length - 1) {
    if (isSeparator(content, i, symbol)) {
      ranges.add(TextRange(i, i + 2))
      i += 2
    }
    else {
      i++
    }
  }
  return ranges
}


/**
 * Inverts a list of excluded ranges to get the remaining content ranges.
 * Input array must be sorted by start offset.
 *
 * Example:
 * For ranges [[2, 3], [5, 7]] and length 12 this method will return [[0, 2], [3, 5], [7, 12]]
 */
private fun invertExcludedToContentRanges(excluded: List<TextRange>, length: Int): List<TextRange> {
  if (excluded.isEmpty()) return listOf(TextRange(0, length))
  val result = ArrayList<TextRange>(excluded.size + 1)

  if (excluded.first().startOffset > 0) result.add(TextRange(0, excluded.first().startOffset))
  for (idx in 0 until excluded.size - 1) {
    val current = excluded[idx]
    val next = excluded[idx + 1]
    if (next.startOffset > current.endOffset) {
      result.add(TextRange(current.endOffset, next.startOffset))
    }
  }
  if (length > excluded.last().endOffset) result.add(TextRange(excluded.last().endOffset, length))

  return result.sortedBy { it.startOffset }
}

private fun isSeparator(text: CharSequence, index: Int, symbol: Char): Boolean = text[index] == '\\' && text[index + 1] == symbol

private fun mapSeparator(symbol: Char): Char {
  return when (symbol) {
    'n' -> '\n'
    't' -> '\t'
    'b' -> ' '
    'r' -> '\r'
    'f' -> '\u000C'
    'v' -> Char(0x0b)
    else -> ' '
  }
}
