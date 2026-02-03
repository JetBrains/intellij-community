// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;

import java.util.Arrays;
import java.util.EmptyStackException;

public final class BooleanStack {
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

  public boolean isEmpty() {
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
