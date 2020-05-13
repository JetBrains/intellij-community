/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

public class CharArrayCharSequence implements CharSequenceBackedByArray, CharSequenceWithStringHash {
  protected final char[] myChars;
  protected final int myStart;
  protected final int myEnd;

  public CharArrayCharSequence(char @NotNull ... chars) {
    this(chars, 0, chars.length);
  }

  public CharArrayCharSequence(char @NotNull [] chars, int start, int end) {
    if (start < 0 || end > chars.length || start > end) {
      throw new IndexOutOfBoundsException("chars.length:" + chars.length + ", start:" + start + ", end:" + end);
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
    return myChars[index + myStart];
  }

  @NotNull
  @Override
  public CharSequence subSequence(int start, int end) {
    return start == 0 && end == length() ? this : new CharArrayCharSequence(myChars, myStart + start, myStart + end);
  }

  @Override
  @NotNull
  public String toString() {
    return new String(myChars, myStart, myEnd - myStart); //TODO StringFactory
  }

  @Override
  public char @NotNull [] getChars() {
    if (myStart == 0) return myChars;
    char[] chars = new char[length()];
    getChars(chars, 0);
    return chars;
  }

  @Override
  public void getChars(char @NotNull [] dst, int dstOffset) {
    System.arraycopy(myChars, myStart, dst, dstOffset, length());
  }

  @Override
  public boolean equals(Object anObject) {
    if (this == anObject) {
      return true;
    }
    if (anObject == null || getClass() != anObject.getClass() || length() != ((CharSequence)anObject).length()) {
      return false;
    }
    return CharArrayUtil.regionMatches(myChars, myStart, myEnd, (CharSequence)anObject);
  }

  /**
   * See {@link java.io.Reader#read(char[], int, int)};
   */
  public int readCharsTo(int start, char[] cbuf, int off, int len) {
    final int readChars = Math.min(len, length() - start);
    if (readChars <= 0) return -1;

    System.arraycopy(myChars, myStart + start, cbuf, off, readChars);
    return readChars;
  }

  private transient int hash;
  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      hash = h = StringUtil.stringHashCode(this, 0, length());
    }
    return h;
  }
}
