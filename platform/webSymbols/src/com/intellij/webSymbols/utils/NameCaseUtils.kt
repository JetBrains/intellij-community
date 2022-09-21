// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils

object NameCaseUtils {
  @JvmStatic
  fun toPascalCase(str: String): String =
    toCamelCase(str, false, true)

  @JvmStatic
  fun toPascalCase(str: String, preserveConsecutiveUppercase: Boolean): String =
    toCamelCase(str, preserveConsecutiveUppercase, true)

  @JvmStatic
  fun toCamelCase(str: String): String =
    toCamelCase(str, false, false)

  @JvmStatic
  fun toCamelCase(str: String, preserveConsecutiveUppercase: Boolean): String =
    toCamelCase(str, preserveConsecutiveUppercase, false)

  @JvmStatic
  fun toKebabCase(str: String): String =
    toSeparatorBasedCase('-', str, false, false, false)

  @JvmStatic
  fun toKebabCase(str: String,
                  noHyphenBeforeDigit: Boolean,
                  noHyphenBetweenDigitAndLowercase: Boolean,
                  splitConsecutiveUppercase: Boolean): String =
    toSeparatorBasedCase('-', str, noHyphenBeforeDigit,
                         noHyphenBetweenDigitAndLowercase,
                         splitConsecutiveUppercase)

  @JvmStatic
  fun toSnakeCase(str: String): String =
    toSeparatorBasedCase('_', str, false, false, false)

  @JvmStatic
  fun toSnakeCase(str: String,
                  noUnderscoreBeforeDigit: Boolean,
                  noUnderscoreBetweenDigitAndLowercase: Boolean,
                  splitConsecutiveUppercase: Boolean): String =
    toSeparatorBasedCase('_', str, noUnderscoreBeforeDigit,
                         noUnderscoreBetweenDigitAndLowercase,
                         splitConsecutiveUppercase)

  /**
   * Satisfies API of a popular `camelcase` NPM package - https://github.com/sindresorhus/camelcase
   */
  private fun toCamelCase(str: String, preserveConsecutiveUppercase: Boolean, pascalCase: Boolean): String {
    val result = StringBuilder()
    val codePoints = str.codePoints().toArray()
    for (i in codePoints.indices) {
      val ch = codePoints[i]
      if (Character.isUpperCase(ch)) {
        val prevChar = if (i > 0) codePoints[i - 1] else 0
        val nextChar = if (i < codePoints.size - 1) codePoints[i + 1] else 0
        if ((result.isEmpty() && (pascalCase || preserveConsecutiveUppercase && Character.isUpperCase(nextChar)))
            || (result.isNotEmpty() && (preserveConsecutiveUppercase
                                        || isCaseSeparator(prevChar)
                                        || isRegularDigit(prevChar)
                                        || !Character.isUpperCase(prevChar)
                                        || Character.isLowerCase(nextChar)))) {
          result.appendCodePoint(ch)
        }
        else {
          result.appendCodePoint(Character.toLowerCase(ch))
        }
      }
      else if (Character.isLowerCase(ch)) {
        val prevChar = if (i > 0) codePoints[i - 1] else 0
        if ((result.isEmpty() && pascalCase)
            || (result.isNotEmpty() && (isCaseSeparator(prevChar) || isRegularDigit(prevChar)))) {
          result.appendCodePoint(Character.toUpperCase(ch))
        }
        else {
          result.appendCodePoint(ch)
        }
      }
      else if (!isCaseSeparator(ch)) {
        result.appendCodePoint(ch)
      }
    }
    return result.toString()
  }

  private fun toSeparatorBasedCase(separator: Char, str: String,
                                   noSeparatorBeforeDigit: Boolean,
                                   noSeparatorBetweenDigitAndLowercase: Boolean,
                                   splitConsecutiveUppercase: Boolean): String {
    val result = StringBuilder()
    val codePoints = str.codePoints().toArray()
    for (i in codePoints.indices) {
      val ch = codePoints[i]
      val prevChar = if (i > 0) codePoints[i - 1] else 0
      val nextChar = if (i + 1 < codePoints.size) codePoints[i + 1] else 0
      if (isCaseSeparator(ch)) {
        if (!isCaseSeparator(nextChar) && nextChar != 0 && result.isNotEmpty()) {
          result.append(separator)
        }
      }
      else if (Character.isUpperCase(ch)) {
        if (!isCaseSeparator(prevChar)
            && result.isNotEmpty()
            && (isRegularDigit(prevChar)
                || !Character.isUpperCase(prevChar)
                || splitConsecutiveUppercase
                || Character.isLowerCase(nextChar))) {
          result.append(separator)
        }
        result.appendCodePoint(Character.toLowerCase(ch))
      }
      else if (Character.isLowerCase(ch)) {
        if (!noSeparatorBetweenDigitAndLowercase && isRegularDigit(prevChar)) {
          result.append(separator)
        }
        result.appendCodePoint(ch)
      }
      else if (!noSeparatorBeforeDigit && isRegularDigit(ch)) {
        if (!isCaseSeparator(prevChar)
            && !isRegularDigit(prevChar)
            && result.isNotEmpty()) {
          result.append(separator)
        }
        result.appendCodePoint(ch)
      }
      else {
        result.appendCodePoint(ch)
      }
    }
    return result.toString()
  }

  private fun isCaseSeparator(codePoint: Int): Boolean =
    codePoint == '_'.code || codePoint == '.'.code || codePoint == '-'.code || codePoint == ' '.code

  private fun isRegularDigit(codePoint: Int): Boolean =
    '0'.code <= codePoint && codePoint <= '9'.code


}