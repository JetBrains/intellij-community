// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import fleet.util.multiplatform.Actual
import java.nio.CharBuffer

/**
 * [com.intellij.util.text.fromSequenceWithoutCopyingPlatformSpecific]
 */
@Actual("fromSequenceWithoutCopyingPlatformSpecific")
internal fun fromSequenceWithoutCopyingPlatformSpecificJvm(seq: CharSequence?): CharArray? {
  if (seq is CharBuffer) {
    val buffer = seq
    if (buffer.hasArray() && !buffer.isReadOnly() && buffer.arrayOffset() == 0 && buffer.position() == 0) {
      return buffer.array()
    }
  }
  return null
}

/**
 * [com.intellij.util.text.getCharsPlatformSpecific]
 */
@Actual("getCharsPlatformSpecific")
internal fun getCharsPlatformSpecificJvm(string: CharSequence, srcOffset: Int, dst: CharArray, dstOffset: Int, len: Int): Boolean {
  if (string is CharBuffer) {
    val buffer = string
    val i = buffer.position()
    buffer.position(i + srcOffset)
    buffer.get(dst, dstOffset, len)
    buffer.position(i)
    return true
  }

  if (string is StringBuffer) {
    string.getChars(srcOffset, srcOffset + len, dst, dstOffset)
    return true
  }

  return false
}