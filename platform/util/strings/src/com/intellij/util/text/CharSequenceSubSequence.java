// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.text;

import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;

public class CharSequenceSubSequence implements CharSequence, CharArrayExternalizable, CharSequenceWithStringHash {
  private final CharSequence myChars;
  private final int myStart;
  private final int myEnd;

  public CharSequenceSubSequence(@NotNull CharSequence chars) {
    this(chars, 0, chars.length());
  }

  public CharSequenceSubSequence(@NotNull CharSequence chars, int start, int end) {
    if (start < 0 || end > chars.length() || start > end) {
      throw new IndexOutOfBoundsException("chars sequence.length:" + chars.length() +
                                          ", start:" + start +
                                          ", end:" + end);
    }
    myChars = chars;
    myStart = start;
    myEnd = end;
  }

  @Override
  public final int length() {
    return myEnd - myStart;
  }

  @Override
  public final char charAt(int index) {
    return myChars.charAt(index + myStart);
  }

  @NotNull
  @Override
  public CharSequence subSequence(int start, int end) {
    if (start == myStart && end == myEnd) return this;
    return new CharSequenceSubSequence(myChars, myStart + start, myStart + end);
  }

  @Override
  @NotNull
  public String toString() {
    if (myChars instanceof String) return ((String)myChars).substring(myStart, myEnd);
    return new String(CharArrayUtil.fromSequence(myChars, myStart, myEnd));
  }

  @NotNull
  CharSequence getBaseSequence() {
    return myChars;
  }

  @Override
  public void getChars(int start, int end, char @NotNull [] dest, int destPos) {
    assert end - start <= myEnd - myStart;
    CharArrayUtil.getChars(myChars, dest, start + myStart, destPos, end - start);
  }

  private transient int hash;
  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      hash = h = Strings.stringHashCode(myChars, myStart, myEnd);
    }
    return h;
  }
}
