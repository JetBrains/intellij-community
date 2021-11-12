// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import org.jetbrains.annotations.Contract

@Contract(pure = true)
fun String?.nullize(nullizeSpaces: Boolean = false): String? = Strings.nullize(this, nullizeSpaces)

fun String.trimMiddle(maxLength: Int, useEllipsisSymbol: Boolean = true): String {
  return StringUtil.shortenTextWithEllipsis(this, maxLength, maxLength shr 1, useEllipsisSymbol)
}

fun CharArray.nullize(): CharArray? = if (isEmpty()) null else this

/**
 * Escapes line-breaks making the string fit one line anyway
 */
fun String.escLBr(): String = StringUtil.escapeLineBreak(this)

fun String.findTextRange(substring: String): TextRange? {
  val start = this.indexOf(substring)
  if (start == -1) return null
  return TextRange.from(start, substring.length)
}