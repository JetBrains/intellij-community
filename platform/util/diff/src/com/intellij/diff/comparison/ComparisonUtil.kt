// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.openapi.util.text.equalsIgnoreWhitespaces
import com.intellij.openapi.util.text.equalsTrimWhitespaces
import com.intellij.openapi.util.text.stringHashCode
import com.intellij.openapi.util.text.stringHashCodeIgnoreWhitespaces
import com.intellij.util.diff.DiffConfig
import org.jetbrains.annotations.Contract
import kotlin.jvm.JvmStatic

object ComparisonUtil {
  @JvmStatic
  fun isEquals(text1: CharSequence?, text2: CharSequence?, policy: ComparisonPolicy): Boolean {
    if (text1 === text2) return true
    if (text1 == null || text2 == null) return false

    when (policy) {
      ComparisonPolicy.DEFAULT -> return text1.equalsByContents(text2)
      ComparisonPolicy.TRIM_WHITESPACES -> return text1.equalsTrimWhitespaces(text2)
      ComparisonPolicy.IGNORE_WHITESPACES -> return text1.equalsIgnoreWhitespaces(text2)
    }
  }

  @JvmStatic
  fun hashCode(text: CharSequence, policy: ComparisonPolicy): Int {
    when (policy) {
      ComparisonPolicy.DEFAULT -> return text.stringHashCode()
      ComparisonPolicy.TRIM_WHITESPACES -> {
        val offset1 = trimStart(text, 0, text.length)
        val offset2 = trimEnd(text, offset1, text.length)

        return text.stringHashCode(offset1, offset2)
      }
      ComparisonPolicy.IGNORE_WHITESPACES -> return text.stringHashCodeIgnoreWhitespaces()
    }
  }

  @JvmStatic
  @Contract(pure = true)
  fun isEqualTexts(text1: CharSequence, text2: CharSequence, policy: ComparisonPolicy): Boolean {
    when (policy) {
      ComparisonPolicy.DEFAULT -> return text1.equalsByContents(text2)
      ComparisonPolicy.TRIM_WHITESPACES -> return equalsTrimWhitespaces(text1, text2)
      ComparisonPolicy.IGNORE_WHITESPACES -> return text1.equalsIgnoreWhitespaces(text2)
    }
  }

  /**
   * Method is different from [Strings.equalsTrimWhitespaces].
   *
   *
   * Here, leading/trailing whitespaces for *inner* lines will be ignored as well.
   * Ex: "\nXY\n" and "\n XY \n" strings are equal, "\nXY\n" and "\nX Y\n" strings are different.
   */
  @JvmStatic
  @Contract(pure = true)
  fun equalsTrimWhitespaces(s1: CharSequence, s2: CharSequence): Boolean {
    var index1 = 0
    var index2 = 0

    while (true) {
      var lastLine1 = false
      var lastLine2 = false

      var end1 = s1.indexOf('\n', index1) + 1
      var end2 = s2.indexOf('\n', index2) + 1
      if (end1 == 0) {
        end1 = s1.length
        lastLine1 = true
      }
      if (end2 == 0) {
        end2 = s2.length
        lastLine2 = true
      }
      if (lastLine1 xor lastLine2) return false

      val line1 = s1.subSequence(index1, end1)
      val line2 = s2.subSequence(index2, end2)
      if (!line1.equalsTrimWhitespaces(line2)) return false

      index1 = end1
      index2 = end2
      if (lastLine1) return true
    }
  }

  @JvmStatic
  fun getUnimportantLineCharCount(): Int {
    return DiffConfig.UNIMPORTANT_LINE_CHAR_COUNT
  }
}

// from StringUtilRt
private fun CharSequence?.equalsByContents(other: CharSequence?): Boolean {
  if (this === other) return true
  if (this == null || other == null) return false

  if (length != other.length) return false

  for (i in 0..<length) {
    if (this[i] != other[i]) {
      return false
    }
  }

  return true
}
