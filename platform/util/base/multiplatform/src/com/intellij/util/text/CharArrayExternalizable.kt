// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import com.intellij.util.text.CharArrayUtilKmp.getChars


/**
 * A char sequence that supports fast copying of its full or partial contents to a char array. May be useful for performance optimizations
 * @see CharSequenceBackedByArray
 *
 * @see CharArrayUtilKmp.getChars
 */
interface CharArrayExternalizable : CharSequence {
  /**
   * Copies own character sub-sequence to the given array
   * @param start the index where to start taking chars from in this sequence
   * @param end the index where to end taking chars in this sequence
   * @param dest the array to put characters into
   * @param destPos the index where to put the characters in the dest array
   */
  fun getChars(start: Int, end: Int, dest: CharArray, destPos: Int)
}
