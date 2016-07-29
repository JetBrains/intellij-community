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

import org.jetbrains.annotations.NotNull;

public class SingleCharSequence implements CharSequence {
  private final char myCh;

  public SingleCharSequence(char ch) {
    myCh = ch;
  }

  @Override
  public int length() {
    return 1;
  }

  @Override
  public char charAt(int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException("Index out of bounds: " + index);
    }
    return myCh;
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if (start == 0 && end == 1) return this;
    if (start < 0) throw new IndexOutOfBoundsException("Start index out of range:" + start);
    if (end > 1 || end < 0) throw new IndexOutOfBoundsException("End index out of range:" + end);
    if (start > end) throw new IndexOutOfBoundsException("Start index should be less or equal to end index:" + start + " - " + end);
    return "";
  }

  @Override
  @NotNull
  public String toString() {
    return String.valueOf(myCh);
  }
}
