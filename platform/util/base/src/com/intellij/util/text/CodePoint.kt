// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

private const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000

/**
 * An abstraction over code points.
 * It's extracted into a separate class, so the clients could replace it with their own implementation (e.g. multiplatform one).
 *
 * Should be replaced when KMP API for codepoint when it's implemented: OSIP-133,
 * or if `util.base.multiplatform` module will depend on `fleet.util.codepoints` implementation and all the clients will moved to
 * the multipaltform module.
 */
@JvmInline
internal value class CodePoint(val codePoint: Int) {
  val charCount: Int
    get() = if (codePoint < MIN_SUPPLEMENTARY_CODE_POINT) 1 else 2
  val unicodeScript: UnicodeScript
    get() = when (Character.UnicodeScript.of(codePoint)) {
      Character.UnicodeScript.COMMON -> UnicodeScript.COMMON
      Character.UnicodeScript.KATAKANA -> UnicodeScript.KATAKANA
      Character.UnicodeScript.HIRAGANA -> UnicodeScript.HIRAGANA
      else -> UnicodeScript.UNKNOWN
    }

  fun isUpperCase(): Boolean = Character.isUpperCase(codePoint)

  fun isLowerCase(): Boolean = Character.isLowerCase(codePoint)
  fun isDigit(): Boolean = Character.isDigit(codePoint)
  fun isLetter(): Boolean = Character.isLetter(codePoint)
  fun isLetterOrDigit(): Boolean = Character.isLetterOrDigit(codePoint)
  fun isIdeographic(): Boolean = Character.isIdeographic(codePoint)
}

internal fun codePointAt(string: String, index: Int): CodePoint = CodePoint(string.codePointAt(index))

internal fun codePointBefore(string: String, index: Int): CodePoint = CodePoint(string.codePointBefore(index))

internal enum class UnicodeScript {
  COMMON,
  HIRAGANA,
  KATAKANA,
  UNKNOWN,
  ;
}
