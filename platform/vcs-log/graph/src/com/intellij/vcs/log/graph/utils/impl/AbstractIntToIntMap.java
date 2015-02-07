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

package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.IntToIntMap;

public abstract class AbstractIntToIntMap implements IntToIntMap {

  @Override
  public int getShortIndex(int longIndex) {
    checkLongIndex(longIndex);

    if (shortSize() == 0 || getLongIndex(0) > longIndex) return 0;
    int a = 0;
    int b = shortSize() - 1;
    while (b > a + 1) {
      int middle = (a + b) / 2;
      if (getLongIndex(middle) <= longIndex) {
        a = middle;
      }
      else {
        b = middle;
      }
    }
    return getLongIndex(b) <= longIndex ? b : a;
  }

  protected void checkLongIndex(int longIndex) {
    if (longIndex < 0 || longIndex >= longSize()) {
      throw new IndexOutOfBoundsException("LongSize is: " + longSize() + ", but longIndex: " + longIndex);
    }
  }

  protected void checkShortIndex(int shortIndex) {
    if (shortIndex < 0 || shortIndex >= shortSize()) {
      throw new IndexOutOfBoundsException("ShortSize is: " + shortSize() + ", but shortIndex: " + shortIndex);
    }
  }

  protected void checkUpdateParameters(int startLongIndex, int endLongIndex) {
    if (startLongIndex < 0 || endLongIndex < startLongIndex || endLongIndex >= longSize()) {
      throw new IllegalArgumentException(
        "ShortSize is: " + shortSize() + ", but updateRequest is: (" + startLongIndex + ", " + endLongIndex + ")");
    }
  }
}
