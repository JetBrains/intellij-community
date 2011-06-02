/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import gnu.trove.TIntArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * String builder for only append and remove from the end without insert.
 *
 * @author Medvedev Max
 */
public class MyStringBuilder implements CharSequence {
  private char[] arr = new char[16];

  private int innerLen = 0;
  private int realLength = 0;

  List<CharSequence> builders = new ArrayList<CharSequence>(4);
  TIntArrayList positions = new TIntArrayList(4);

  @Override
  public int length() {
    return realLength;
  }

  @Override
  public char charAt(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    throw new UnsupportedOperationException();
  }

  public MyStringBuilder append(char c) {
    if (innerLen == arr.length) {
      final char[] chars = new char[innerLen * 2];
      System.arraycopy(arr, 0, chars, 0, arr.length);
      arr = chars;
    }
    arr[innerLen] = c;
    innerLen++;
    realLength++;
    return this;
  }

  public MyStringBuilder append(CharSequence s) {
    builders.add(s);
    positions.add(innerLen);
    realLength += s.length();
    return this;
  }

  public MyStringBuilder removeFromTheEnd(int count) {
    if (positions.size() == 0) {
      innerLen -= count;
      realLength -= count;
      return this;
    }

    final int lastIndex = positions.size() - 1;
    final int lastPos = positions.getQuick(lastIndex);

    if (lastPos == innerLen) {
      final CharSequence last = builders.get(lastIndex);
      final int lastLength = last.length();
      if (lastLength <= count) {
        positions.remove(lastIndex);
        builders.remove(lastIndex);
        realLength -= lastLength;
        count -= lastLength;
        if (count > 0) removeFromTheEnd(count);
      }
      else {
        if (last instanceof MyStringBuilder) {
          ((MyStringBuilder)last).removeFromTheEnd(count);
          realLength -= count;
        }
        else {
          positions.remove(lastIndex);
          builders.remove(lastIndex);
          realLength -= lastLength;
          int toAppend = lastLength - count;
          for (int i = 0; i < toAppend; i++) {
            append(last.charAt(i));
          }
        }
      }
    }
    else {
      if (lastPos+count<=innerLen) {
        innerLen-=count;
        realLength-=count;
      }
      else {
        final int removed = innerLen - lastPos;
        innerLen -= removed;
        realLength -= removed;
        count -= removed;
        if (count > 0) removeFromTheEnd(count);
      }
    }
    return this;
  }

  MyStringBuilder append(Object value) {
    return append(String.valueOf(value));
  }

  @Override
  public String toString() {
    if (positions.size() == 0) {
      return new String(arr);
    }

    char[] chars = new char[realLength];
    innerToString(chars, 0);
    return new String(chars);
  }

  private int innerToString(char[] chars, int posInChars) {
    int posInInner = 0;
    final int size = positions.size();
    for (int i = 0; i < size; i++) {
      final int pos = positions.get(i);
      final int length = pos - posInInner;
      System.arraycopy(arr, posInInner, chars, posInChars, length);
      posInChars += length;
      posInInner += length;
      CharSequence seq = builders.get(i);
      if (seq instanceof MyStringBuilder) {
        posInChars = ((MyStringBuilder)seq).innerToString(chars, posInChars);
      }
      else {
        final int seqLength = seq.length();
        for (int j = 0; j < seqLength; j++) {
          chars[posInChars] = seq.charAt(j);
          posInChars++;
        }
      }
    }

    final int length = innerLen - posInInner;
    System.arraycopy(arr, posInInner, chars, posInChars, length);
    return posInChars + length;
  }
}
