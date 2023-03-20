// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Treats byte array as a sequence of chars in {@link StandardCharsets#US_ASCII} encoding
 */
@ReviseWhenPortedToJDK("9")
public final class ByteArrayCharSequence implements CharSequenceWithStringHash {
  private final int myStart;
  private final int myEnd;
  private transient int hash;
  private final byte[] myChars;

  public ByteArrayCharSequence(byte @NotNull [] chars) {
    this(chars, 0, chars.length);
  }
  public ByteArrayCharSequence(byte @NotNull [] chars, int start, int end) {
    myChars = chars;
    myStart = start;
    myEnd = end;
  }

  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      hash = h = Strings.stringHashCode(this, 0, length());
    }
    return h;
  }

  @Override
  public int length() {
    return myEnd - myStart;
  }

  @Override
  public char charAt(int index) {
    return (char)(myChars[index + myStart] & 0xff);
  }

  @NotNull
  @Override
  public CharSequence subSequence(int start, int end) {
    return start == 0 && end == length() ? this : new ByteArrayCharSequence(myChars, myStart + start, myStart + end);
  }

  @Override
  @NotNull
  public String toString() {
    return new String(myChars, myStart, length(), StandardCharsets.ISO_8859_1);
  }

  public void getChars(int start, int end, char[] dest, int pos) {
    for (int idx = start; idx < end; idx++) {
      dest[idx - start + pos] = (char)(myChars[idx + myStart] & 0xFF); 
    }
  }
}
