// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TextRangeUtils")
@file:ApiStatus.Experimental
package com.intellij.util.text

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

fun splitLineRanges(newText0: CharSequence): Sequence<TextRange> = splitToTextRanges(newText0, "\n", true)

fun splitToTextRanges(charSequence: CharSequence, delimiter: String, includeDelimiter: Boolean = false): Sequence<TextRange> {
  var lastMatch = 0
  var lastSplit = 0
  return sequence {
    while (true) {
      val start = charSequence.indexOf(delimiter, lastMatch)
      if (start == -1) {
        yield(TextRange(lastSplit, charSequence.length))
        return@sequence
      }
      lastMatch = start + delimiter.length
      yield(TextRange(lastSplit, if (includeDelimiter) lastMatch else start))
      lastSplit = lastMatch
      if (lastMatch == charSequence.length)
        return@sequence
    }
  }
}