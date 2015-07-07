/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.text;

import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.StringUtil;
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

  @NotNull
  public String toString() {
    if (myChars instanceof String) return ((String)myChars).substring(myStart, myEnd);
    return StringFactory.createShared(CharArrayUtil.fromSequence(myChars, myStart, myEnd));
  }

  @NotNull
  public CharSequence getBaseSequence() {
    return myChars;
  }

  @Override
  public void getChars(int start, int end, @NotNull char[] dest, int destPos) {
    assert end - start <= myEnd - myStart;
    CharArrayUtil.getChars(myChars, dest, start + myStart, destPos, end - start);
  }

  private int hash;
  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      hash = h = StringUtil.stringHashCode(this, 0, length());
    }
    return h;
  }
}
