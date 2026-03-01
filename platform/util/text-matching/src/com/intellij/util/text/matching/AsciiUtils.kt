// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching

/**
 * String utility methods that assume that the string content is ASCII-only (all codepoints are <= 127).
 * These methods may work faster but work incorrectly on other characters
 */
internal object AsciiUtils {
  /**
   * Implementation of [com.intellij.util.text.NameUtilCore.nextWord] for ASCII-only strings
   *
   * @param text text to find the next word in
   * @param start starting position within the text
   * @return position of the next word; may point to the end of the string
   */
  fun nextWordAscii(text: String, start: Int): Int {
    if (!isLetterOrDigitAscii(text[start])) {
      return start + 1
    }

    var i = start
    while (i < text.length && isDigitAscii(text[i])) {
      i++
    }
    if (i > start) {
      // digits form a separate hump
      return i
    }

    while (i < text.length && isUpperAscii(text[i])) {
      i++
    }

    if (i > start + 1) {
      // several consecutive uppercase letters form a hump
      if (i == text.length || !isLetterAscii(text[i])) {
        return i
      }
      return i - 1
    }

    if (i == start) i += 1
    while (i < text.length && isLetterAscii(text[i]) && !isWordStartAscii(text, i)) {
      i++
    }
    return i
  }

  private fun isWordStartAscii(text: String, i: Int): Boolean {
    val cur = text[i]
    val prev = if (i > 0) text[i - 1] else null
    if (isUpperAscii(cur)) {
      if (prev != null && isUpperAscii(prev)) {
        // check that we're not in the middle of an all-caps word
        val nextPos = i + 1
        if (nextPos >= text.length) return false
        return isLowerAscii(text[nextPos])
      }
      return true
    }
    if (isDigitAscii(cur)) {
      return true
    }
    if (!isLetterAscii(cur)) {
      return false
    }
    return i == 0 || !isLetterOrDigitAscii(text[i - 1])
  }

  private fun isLetterAscii(cur: Char): Boolean {
    return cur in 'a'..'z' || cur in 'A'..'Z'
  }

  private fun isLetterOrDigitAscii(cur: Char): Boolean {
    return isLetterAscii(cur) || isDigitAscii(cur)
  }

  private fun isDigitAscii(cur: Char): Boolean {
    return cur in '0'..'9'
  }

  fun toUpperAscii(c: Char): Char {
    return if (isLowerAscii(c)) {
      // 0x20. It's 'A'.code - 'a'.code, kotlin doesn't fold the constant during compilation
      c - 0x20
    }
    else {
      c
    }
  }

  fun toLowerAscii(c: Char): Char {
    return if (isUpperAscii(c)) {
      // 0x20. It's 'A'.code - 'a'.code, kotlin doesn't fold the constant during compilation
      c + 0x20
    }
    else {
      c
    }
  }

  fun isUpperAscii(c: Char): Boolean {
    return c in 'A'..'Z'
  }

  fun isLowerAscii(c: Char): Boolean {
    return c in 'a'..'z'
  }

  /**
   * @param string string to check
   * @return true if a given string contains ASCII-only characters, so it can be processed with other methods in this class
   */
  fun isAscii(string: String): Boolean {
    for (c in string) {
      if (!isAscii(c)) {
        return false
      }
    }
    return true
  }

  fun isAscii(c: Char): Boolean = c.code < 128
}