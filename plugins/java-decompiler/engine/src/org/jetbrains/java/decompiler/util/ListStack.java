// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

public class ListStack<T> implements Iterable<T> {
  protected final LinkedList<T> list;

  public ListStack() {
    this.list = new LinkedList<>();
  }

  public ListStack(ListStack<T> other) {
    this.list = new LinkedList<>(other.list);
  }

  /** Pushes an entry on the end */
  public void push(T item) {
    list.addLast(item);
  }

  /** Pops one entry from the end */
  public T pop() {
    return list.removeLast();
  }

  /** Pops several entries, removing the last one */
  public T pop(int count) {
    T o = null;
    for (int i = 0; i < count; i++) {
      o = this.pop();
    }
    return o;
  }

  /**
   * Returns an element at the given offset from the end of the collection.
   * 1 is at the very end, 2 is one from the end, etc.
   */
  public T peek(int offset) {
    return list.get(list.size() - offset);
  }

  /**
   * Inserts an element at the given offset from the end of the collection.
   * 1 is at the very end, 2 is one from the end, etc.
   */
  public void insert(int offset, T item) {
    list.add(list.size() - offset, item);
  }

  public void clear() {
    list.clear();
  }

  public int size() {
    return list.size();
  }

  @Override
  public Iterator<T> iterator() {
    return list.iterator();
  }

  public ListIterator<T> listIterator() {
    return list.listIterator();
  }
}