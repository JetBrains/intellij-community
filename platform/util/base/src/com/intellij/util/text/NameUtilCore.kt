// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

object NameUtilCore {
  private const val KANA_START = 0x3040
  private const val KANA_END = 0x3358
  private const val KANA2_START = 0xFF66
  private const val KANA2_END = 0xFF9D

  /**
   * Splits an identifier into words, separated with underscores or upper-case characters
   * (camel-case).
   *
   * @param name the identifier to split.
   * @return the list of strings into which the identifier has been split.
   */
  @JvmStatic
  fun splitNameIntoWordList(name: String): List<String> {
    val underlineDelimited = name.split('_')
    val result = mutableListOf<String>()
    for (word in underlineDelimited) {
      var start = 0
      while (start < word.length) {
        val next = nextWord(word, start)
        result.add(word.substring(start, next))
        start = next
      }
    }
    return result
  }

  /**
   * @param name the identifier to split.
   * @return the array of strings into which the identifier has been split.
   */
  @JvmStatic
  @Deprecated(
    """use {@link #splitNameIntoWordList(String)} to avoid redundant allocations
    <p>
    Splits an identifier into words, separated with underscores or upper-case characters
    (camel-case).
   
    """)
  fun splitNameIntoWords(name: String): Array<String> {
    return splitNameIntoWordList(name).toTypedArray()
  }

  @JvmStatic
  fun nextWord(text: String, start: Int): Int {
    val ch = codePointAt(text, start)
    val chLen = ch.charCount
    if (!ch.isLetterOrDigit()) {
      return start + chLen
    }

    var i = start
    while (i < text.length) {
      val codePoint = codePointAt(text, i)
      if (!codePoint.isDigit()) break
      i += codePoint.charCount
    }
    if (i > start) {
      // digits form a separate hump
      return i
    }

    while (i < text.length) {
      val codePoint = codePointAt(text, i)
      if (!codePoint.isUpperCase()) break
      i += codePoint.charCount
    }

    if (i > start + chLen) {
      // several consecutive uppercase letters form a hump
      if (i == text.length || !codePointAt(text, i).isLetter()) {
        return i
      }
      return i - codePointBefore(text, i).charCount
    }

    if (i == start) i += chLen
    while (i < text.length) {
      val codePoint = codePointAt(text, i)
      if (!codePoint.isLetter() || isWordStart(text, i)) break
      i += codePoint.charCount
    }
    return i
  }

  @JvmStatic
  fun isWordStart(text: String, i: Int): Boolean {
    val cur = codePointAt(text, i)
    val prev = if (i > 0) codePointBefore(text, i) else null
    if (cur.isUpperCase()) {
      if (prev != null && prev.isUpperCase()) {
        // check that we're not in the middle of an all-caps word
        val nextPos = i + cur.charCount
        return nextPos < text.length && codePointAt(text, nextPos).isLowerCase()
      }
      return true
    }
    if (cur.isDigit()) {
      return true
    }
    if (!cur.isLetter()) {
      return false
    }
    if (cur.isIdeographic()) {
      // Consider every ideograph as a separate word
      return true
    }
    return i == 0 || !text[i - 1].isLetterOrDigit() || (prev != null && isKanaBreak(cur, prev))
  }

  private fun maybeKana(codePoint: CodePoint): Boolean {
    return codePoint.codePoint in KANA_START..KANA_END ||
           codePoint.codePoint in KANA2_START..KANA2_END
  }

  private fun isKanaBreak(cur: CodePoint, prev: CodePoint): Boolean {
    if (!maybeKana(cur) && !maybeKana(prev)) return false
    val curScript = cur.unicodeScript
    val prevScript = prev.unicodeScript
    if (prevScript == curScript) return false
    return (curScript == UnicodeScript.KATAKANA ||
            curScript == UnicodeScript.HIRAGANA ||
            prevScript == UnicodeScript.KATAKANA ||
            prevScript == UnicodeScript.HIRAGANA) && prevScript != UnicodeScript.COMMON && curScript != UnicodeScript.COMMON
  }

  @JvmStatic
  @Deprecated("use {@link #nameToWordList(String)} to avoid redundant allocations")
  fun nameToWords(name: String): Array<String> {
    return nameToWordList(name).toTypedArray()
  }

  @JvmStatic
  fun nameToWordList(name: String): List<String> {
    val array = mutableListOf<String>()
    var index = 0

    while (index < name.length) {
      val wordStart = index
      var upperCaseCount = 0
      var lowerCaseCount = 0
      var digitCount = 0
      var specialCount = 0
      while (index < name.length) {
        val c = name[index]
        if (c.isDigit()) {
          if (upperCaseCount > 0 || lowerCaseCount > 0 || specialCount > 0) break
          digitCount++
        }
        else if (c.isUpperCase()) {
          if (lowerCaseCount > 0 || digitCount > 0 || specialCount > 0) break
          upperCaseCount++
        }
        else if (c.isLowerCase()) {
          if (digitCount > 0 || specialCount > 0) break
          if (upperCaseCount > 1) {
            index--
            break
          }
          lowerCaseCount++
        }
        else {
          if (upperCaseCount > 0 || lowerCaseCount > 0 || digitCount > 0) break
          specialCount++
        }
        index++
      }
      val word = name.subSequence(wordStart, index)
      if (word.isNotBlank()) {
        array.add(word.toString())
      }
    }
    return array
  }
}