// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import com.intellij.util.ArrayUtilRt

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
    return ArrayUtilRt.toStringArray(splitNameIntoWordList(name))
  }

  @JvmStatic
  fun nextWord(text: String, start: Int): Int {
    val ch = text.codePointAt(start)
    val chLen = Character.charCount(ch)
    if (!Character.isLetterOrDigit(ch)) {
      return start + chLen
    }

    var i = start
    while (i < text.length) {
      val codePoint = text.codePointAt(i)
      if (!Character.isDigit(codePoint)) break
      i += Character.charCount(codePoint)
    }
    if (i > start) {
      // digits form a separate hump
      return i
    }

    while (i < text.length) {
      val codePoint = text.codePointAt(i)
      if (!Character.isUpperCase(codePoint)) break
      i += Character.charCount(codePoint)
    }

    if (i > start + chLen) {
      // several consecutive uppercase letters form a hump
      if (i == text.length || !Character.isLetter(text.codePointAt(i))) {
        return i
      }
      return i - Character.charCount(text.codePointBefore(i))
    }

    if (i == start) i += chLen
    while (i < text.length) {
      val codePoint = text.codePointAt(i)
      if (!Character.isLetter(codePoint) || isWordStart(text, i)) break
      i += Character.charCount(codePoint)
    }
    return i
  }

  @JvmStatic
  fun isWordStart(text: String, i: Int): Boolean {
    val cur = text.codePointAt(i)
    val prev = if (i > 0) text.codePointBefore(i) else -1
    if (Character.isUpperCase(cur)) {
      if (Character.isUpperCase(prev)) {
        // check that we're not in the middle of an all-caps word
        val nextPos = i + Character.charCount(cur)
        return nextPos < text.length && Character.isLowerCase(text.codePointAt(nextPos))
      }
      return true
    }
    if (Character.isDigit(cur)) {
      return true
    }
    if (!Character.isLetter(cur)) {
      return false
    }
    if (Character.isIdeographic(cur)) {
      // Consider every ideograph as a separate word
      return true
    }
    return i == 0 || !text[i - 1].isLetterOrDigit() || isKanaBreak(cur, prev)
  }

  private fun maybeKana(codePoint: Int): Boolean {
    return codePoint in KANA_START..KANA_END ||
           codePoint in KANA2_START..KANA2_END
  }

  private fun isKanaBreak(cur: Int, prev: Int): Boolean {
    if (!maybeKana(cur) && !maybeKana(prev)) return false
    val curScript = Character.UnicodeScript.of(cur)
    val prevScript = Character.UnicodeScript.of(prev)
    if (prevScript == curScript) return false
    return (curScript == Character.UnicodeScript.KATAKANA ||
            curScript == Character.UnicodeScript.HIRAGANA ||
            prevScript == Character.UnicodeScript.KATAKANA ||
            prevScript == Character.UnicodeScript.HIRAGANA) && prevScript != Character.UnicodeScript.COMMON && curScript != Character.UnicodeScript.COMMON
  }

  @JvmStatic
  @Deprecated("use {@link #nameToWordList(String)} to avoid redundant allocations")
  fun nameToWords(name: String): Array<String> {
    return ArrayUtilRt.toStringArray(nameToWordList(name))
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
