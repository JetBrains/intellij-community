package com.intellij.grazie.utils

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.grazie.text.TextContent
import com.intellij.openapi.util.TextRange

fun TextContent.replaceBackslashEscapes(): TextContent {
  val text = this.replaceBackslashEscapedWhitespace()
  val offsets = IntArray(text.length + 1)
  CodeInsightUtilCore.parseStringCharacters(text.toString(), offsets)
  val exclusions = (1 until offsets.size).asSequence()
    .filter { i -> offsets[i] != 0 && offsets[i] - offsets[i - 1] != 1 }
    .map { index -> keepTrailingWhitespaces(text, offsets, index) }
    .map(TextContent.Exclusion::markUnknown)
    .toList()
  return text.excludeRanges(exclusions)
}

fun TextContent.replaceBackslashEscapedWhitespace(): TextContent {
  return this.replaceBackslashEscapedWhitespace('n').replaceBackslashEscapedWhitespace('t')
}

private fun TextContent.replaceBackslashEscapedWhitespace(separator: Char): TextContent {
  val excluded = getBackslashExcludeRanges(this, separator)
  if (excluded.isEmpty()) return this

  val components = invertExcludedToContentRanges(excluded, this.length)
    .mapNotNull { this.subText(it) }
  return if (components.size > 1) TextContent.joinWithWhitespace(mapSeparator(separator), components)!! else this
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

private fun keepTrailingWhitespaces(text: TextContent, offsets: IntArray, index: Int): TextRange {
  var offset = 0
  while (text[index - 1 + offset].isWhitespace()) offset++
  return TextRange(offsets[index - 1] + offset, offsets[index])
}

private fun isSeparator(text: CharSequence, index: Int, symbol: Char): Boolean = text[index] == '\\' && (text[index + 1] == symbol)

private fun mapSeparator(symbol: Char): Char {
  return when (symbol) {
    'n' -> '\n'
    't' -> '\t'
    else -> ' '
  }
}
