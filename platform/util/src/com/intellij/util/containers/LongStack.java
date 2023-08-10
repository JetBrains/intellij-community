// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;

import java.util.Arrays;
import java.util.EmptyStackException;

public final class LongStack {
  private long[] data;
  private int size;
  public LongStack(int initialCapacity) {
    data = new long[initialCapacity];
    size = 0;
  }

  public LongStack() {
    this(5);
  }

  public void push(long t) {
    if (size >= data.length) {
      data = ArrayUtil.realloc(data, data.length * 3 / 2);
    }
    data[size++] = t;
  }

  public long peek() {
    if (size == 0) throw new EmptyStackException();
    return data[size - 1];
  }

  public long pop() {
    if (size == 0) throw new EmptyStackException();
    return data[--size];
  }

  public boolean empty() {
    return size == 0;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof LongStack) {
      LongStack otherStack = (LongStack)o;
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
