// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:JvmName("StringsKmp")

package com.intellij.openapi.util.text

import com.intellij.util.text.CharSequenceSubSequence
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

@Contract(pure = true)
fun CharSequence.stringHashCode(): Int {
  if (this is String || this is CharSequenceWithStringHash) {
    // we know for sure these classes have conformant (and maybe faster) hashCode()
    return hashCode()
  }

  return this.stringHashCode(0, length)
}

@JvmOverloads
@Contract(pure = true)
fun CharSequence.stringHashCode(from: Int, to: Int, prefixHash: Int = 0): Int {
  var h = prefixHash
  for (off in from..<to) {
    h = 31 * h + get(off).code
  }
  return h
}

@Contract(pure = true)
fun CharArray.stringHashCode(from: Int, to: Int): Int {
  var h = 0
  for (off in from..<to) {
    h = 31 * h + this[off].code
  }
  return h
}

@Contract(pure = true)
fun CharSequence.stringHashCodeIgnoreWhitespaces(): Int {
  var h = 0
  for (off in 0..<length) {
    val c = this[off]
    if (!c.isSpaceEnterOrTab()) {
      h = 31 * h + c.code
    }
  }
  return h
}


@Contract(pure = true)
fun CharSequence.equalsIgnoreWhitespaces(other: CharSequence): Boolean {
  val len1 = this.length
  val len2 = other.length

  var index1 = 0
  var index2 = 0
  while (index1 < len1 && index2 < len2) {
    if (this[index1] == other[index2]) {
      index1++
      index2++
      continue
    }

    var skipped = false
    while (index1 != len1 && this[index1].isSpaceEnterOrTab()) {
      skipped = true
      index1++
    }
    while (index2 != len2 && other[index2].isSpaceEnterOrTab()) {
      skipped = true
      index2++
    }

    if (!skipped) return false
  }

  while (index1 != len1) {
    if (!this[index1].isSpaceEnterOrTab()) return false
    index1++
  }
  while (index2 != len2) {
    if (!other[index2].isSpaceEnterOrTab()) return false
    index2++
  }

  return true
}


@Contract(pure = true)
fun CharSequence.equalsTrimWhitespaces(other: CharSequence): Boolean {
  return trimWhitespace().equalsByContents(other.trimWhitespace())
}

private fun CharSequence.trimWhitespace(): CharSequence {
  var start = 0
  var end = length
  while (start < end) {
    val c = this[start]
    if (!c.isSpaceEnterOrTab()) break
    start++
  }

  while (start < end) {
    val c = this[end - 1]
    if (!c.isSpaceEnterOrTab()) break
    end--
  }
  return CharSequenceSubSequence(this, start, end)
}

// from Strings.isWhiteSpace
private fun Char.isSpaceEnterOrTab(): Boolean = this == '\n' || this == '\t' || this == ' '

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
