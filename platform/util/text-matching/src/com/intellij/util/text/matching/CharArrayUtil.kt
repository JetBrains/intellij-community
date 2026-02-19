// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching

internal fun indexOf(s: String, c: Char, start: Int, end: Int, ignoreCase: Boolean): Int {
  for (i in start.coerceAtLeast(0)..<end.coerceAtMost(s.length)) {
    if (s[i].equals(c, ignoreCase)) return i
  }
  return -1
}

internal fun indexOf(s: CharArray, c: Char, start: Int, end: Int, ignoreCase: Boolean): Int {
  for (i in start.coerceAtLeast(0)..<end.coerceAtMost(s.size)) {
    if (s[i].equals(c, ignoreCase)) return i
  }
  return -1
}

internal fun indexOfAny(s: String, chars: CharArray, start: Int, end: Int): Int {
  if (chars.isEmpty()) return -1
  for (i in start.coerceAtLeast(0)..<end.coerceAtMost(s.length)) {
    if (chars.contains(s[i])) return i
  }
  return -1
}

internal fun regionMatches(s1: CharSequence, start: Int, end: Int, s2: CharSequence): Boolean {
  val len = s1.length
  if (start < 0 || start + len > end) return false
  for (i in 0 until len) {
    if (s1[i] != s2[i + start]) return false
  }
  return true
}