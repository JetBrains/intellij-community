// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.util.ArrayList;

public class ListStack<T> extends ArrayList<T> {
  private int pointer = 0;

  public ListStack() {
    super();
  }

  public ListStack(int initialCapacity) {
    super(initialCapacity);
  }

  private ListStack(ArrayList<? extends T> list) {
    super(list);
  }

  public ListStack<T> copy() {
    ListStack<T> copy = new ListStack<>(this);
    copy.pointer = this.pointer;
    return copy;
  }

  public void push(T item) {
    this.add(item);
    pointer++;
  }

  public T pop() {
    pointer--;
    T o = this.get(pointer);
    this.remove(pointer);
    return o;
  }

  public T pop(int count) {
    T o = null;
    for (int i = count; i > 0; i--) {
      o = this.pop();
    }
    return o;
  }

  public void removeMultiple(int count) {
    while (count > 0) {
      pointer--;
      this.remove(pointer);
      count--;
    }
  }

  public T getByOffset(int offset) {
    return this.get(pointer + offset);
  }

  public void insertByOffset(int offset, T item) {
    this.add(pointer + offset, item);
    pointer++;
  }

  @Override
  public void clear() {
    super.clear();
    pointer = 0;
  }
}
