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

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;

import java.util.Arrays;
import java.util.EmptyStackException;

public class BooleanStack {
  private boolean[] data;
  private int size;
  public BooleanStack(int initialCapacity) {
    data = new boolean[initialCapacity];
    size = 0;
  }

  public BooleanStack() {
    this(5);
  }

  public void push(boolean t) {
    if (size >= data.length) {
      data = ArrayUtil.realloc(data, data.length * 3 / 2);
    }
    data[size++] = t;
  }

  public boolean peek() {
    if (size == 0) throw new EmptyStackException();
    return data[size - 1];
  }

  public boolean pop() {
    if (size == 0) throw new EmptyStackException();
    return data[--size];
  }

  public int size() {
    return size;
  }

  public boolean empty() {
    return size == 0;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof BooleanStack) {
      BooleanStack otherStack = (BooleanStack)o;
      if (size != otherStack.size) return false;
      for (int i = 0; i < otherStack.size; i++) {
        if (data[i] != otherStack.data[i]) return false;
      }
      return true;
    }

    return false;
  }

  public void clear() {
    size = 0;
  }

  @Override
  public String toString() {
    return Arrays.toString(Arrays.copyOf(data, size));
  }
}
