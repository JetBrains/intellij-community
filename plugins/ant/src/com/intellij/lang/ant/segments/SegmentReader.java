// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.segments;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.rt.ant.execution.PoolOfDelimiters;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public class SegmentReader {
  private final @NonNls String myString;
  private final char[] myChars;
  private int myPosition = 0;

  public SegmentReader(final @NonNls String packet) {
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

  public @NlsSafe String readLimitedString() {
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
    if (count == 0) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    final ArrayList<String> strings = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      strings.add(readLimitedString());
    }
    return strings.toArray(new String[count]);
  }
}
