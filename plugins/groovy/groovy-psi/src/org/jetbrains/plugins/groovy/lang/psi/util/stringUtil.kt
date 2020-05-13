// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

fun isValidSingleQuotedStringContent(text: String): Boolean = isValidContent(text, '\'', '\n')

fun isValidDoubleQuotedStringContent(text: String): Boolean = isValidContent(text, '"', '$', '\n')

private fun isValidContent(text: String, vararg escapedChars: Char): Boolean {
  val length: Int = text.length
  var idx = 0
  while (idx < length) {
    val ch: Char = text[idx]
    if (ch in escapedChars) {
      return false
    }
    if (ch == '\\') {
      if (idx + 1 < length) {
        val nextCh: Char = text[idx + 1]
        if (nextCh == '\\' || nextCh in escapedChars) {
          idx += 2
          continue
        }
      }
    }
    idx++
  }
  return true
}
