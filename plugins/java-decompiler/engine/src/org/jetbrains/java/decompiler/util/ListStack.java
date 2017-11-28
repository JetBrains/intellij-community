// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.util.ArrayList;

public class ListStack<T> extends ArrayList<T> {

  protected int pointer = 0;

  public ListStack() {
    super();
  }

  public ListStack(ArrayList<T> list) {
    super(list);
  }

  public ListStack<T> clone() {
    ListStack<T> newstack = new ListStack<>(this);
    newstack.pointer = this.pointer;
    return newstack;
  }

  public T push(T item) {
    this.add(item);
    pointer++;
    return item;
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

  public void remove() {
    pointer--;
    this.remove(pointer);
  }

  public void removeMultiple(int count) {
    while (count > 0) {
      pointer--;
      this.remove(pointer);
      count--;
    }
  }

  public boolean empty() {
    return (pointer == 0);
  }

  public int getPointer() {
    return pointer;
  }

  public T getByOffset(int offset) {
    return this.get(pointer + offset);
  }

  public void insertByOffset(int offset, T item) {
    this.add(pointer + offset, item);
    pointer++;
  }

  public void clear() {
    super.clear();
    pointer = 0;
  }
}
