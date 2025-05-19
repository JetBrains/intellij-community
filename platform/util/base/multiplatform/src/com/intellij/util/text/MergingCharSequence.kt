// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text

class MergingCharSequence(private val s1: CharSequence, private val s2: CharSequence) : CharSequence {
  override val length: Int
    get() = s1.length + s2.length

  override fun get(index: Int): Char {
    if (index < s1.length) return s1[index]
    return s2[index - s1.length]
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = when {
    startIndex == 0 && endIndex == length -> this
    startIndex < s1.length && endIndex < s1.length -> s1.subSequence(startIndex, endIndex)
    startIndex >= s1.length && endIndex >= s1.length -> s2.subSequence(startIndex - s1.length, endIndex - s1.length)
    else -> MergingCharSequence(s1.subSequence(startIndex, s1.length), s2.subSequence(0, endIndex - s1.length))
  }

  override fun toString(): String {
    return s1.toString() + s2.toString()
  }
}
