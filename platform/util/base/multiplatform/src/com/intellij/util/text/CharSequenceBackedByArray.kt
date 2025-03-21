// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import org.jetbrains.annotations.ApiStatus

/**
 * A char sequence based on a char array. May be used for performance optimizations.
 *
 * @author Maxim.Mossienko
 * @see CharArrayExternalizable
 *
 * @see CharArrayUtil.getChars
 * @see CharArrayUtil.fromSequenceWithoutCopying
 */
@ApiStatus.Internal
interface CharSequenceBackedByArray : CharSequence {
  // NOT guaranteed to return the array of the length of the original charSequence.length() - may be more for performance reasons.
  val chars: CharArray

  fun getChars(dst: CharArray, dstOffset: Int)
}
