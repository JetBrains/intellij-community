// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.text;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

/**
 * A char sequence based on a char array. May be used for performance optimizations.
 * 
 * @author Maxim.Mossienko
 * @see CharArrayExternalizable
 * @see CharArrayUtil#getChars(CharSequence, char[], int)
 * @see CharArrayUtil#fromSequenceWithoutCopying(CharSequence)
 */
@Internal
public interface CharSequenceBackedByArray extends CharSequence {
  // NOT guaranteed to return the array of the length of the original charSequence.length() - may be more for performance reasons.
  char @NotNull [] getChars();

  void getChars(char @NotNull [] dst, int dstOffset);
}
