/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.string;

import com.intellij.openapi.diff.impl.string.DiffString;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class DiffStringBuilder implements CharSequence {
  @NotNull private char[] myData;
  private int myLength;

  public DiffStringBuilder() {
    this(16);
  }

  public DiffStringBuilder(int len) {
    myData = new char[len];
    myLength = 0;
  }

  @Override
  public int length() {
    return myLength;
  }

  @Override
  public char charAt(int index) {
    if (index < 0 || index >= myLength) {
      throw new StringIndexOutOfBoundsException(index);
    }
    return myData[index];
  }

  @Override
  @NotNull
  public CharSequence subSequence(int start, int end) {
    DiffString.checkBounds(start, end, myLength);
    return DiffString.create(myData, start, end - start);
  }

  @NotNull
  public DiffString toDiffString() {
    return DiffString.create(myData, 0, myLength);
  }

  @Override
  @NotNull
  public String toString() {
    return toDiffString().toString();
  }

  private void ensureCapacityInternal(int neededCapacity) {
    if (neededCapacity > myData.length) {
      int newCapacity = myData.length;
      while (newCapacity < neededCapacity) newCapacity *= 2;

      myData = Arrays.copyOf(myData, newCapacity);
    }
  }

  public void append(@NotNull DiffString s) {
    if (s.isEmpty()) return;
    ensureCapacityInternal(myLength + s.length());
    s.copyData(myData, myLength);
    myLength += s.length();
  }
}
