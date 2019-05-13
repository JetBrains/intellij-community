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
package com.intellij.lang.ant.segments;

import com.intellij.rt.ant.execution.PoolOfDelimiters;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;

public class SegmentReader {
  private final String myString;
  private final char[] myChars;
  private int myPosition = 0;

  public SegmentReader(final String packet) {
    myString = packet;
    myChars = packet.toCharArray();
  }

  public String upTo(final char symbol) {
    int position = myPosition;
    while (position < myChars.length && myChars[position] != symbol) position++;
    final String result = advanceTo(position);
    skip(1);
    return result;
  }

  public void skip(final int count) {
    myPosition = Math.min(myChars.length, myPosition + count);
  }

  public String upToEnd() {
    return advanceTo(myChars.length);
  }

  private String advanceTo(final int position) {
    final String result = myString.substring(myPosition, position);
    myPosition = position;
    return result;
  }

  public String readLimitedString() {
    final int symbolCount = readInt();
    return advanceTo(myPosition + symbolCount);
  }

  public int readInt() {
    final String intString = upTo(PoolOfDelimiters.INTEGER_DELIMITER);
    return Integer.parseInt(intString);
  }

  public long readLong() {
    final String longString = upTo(PoolOfDelimiters.INTEGER_DELIMITER);
    return Long.parseLong(longString);
  }

  public char readChar() {
    myPosition++;
    return myChars[myPosition - 1];
  }

  public boolean isAtEnd() {
    return myPosition == myChars.length;
  }

  public String[] readStringArray() {
    final int count = readInt();
    if (count == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    final ArrayList<String> strings = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      strings.add(readLimitedString());
    }
    return strings.toArray(new String[count]);
  }
}
