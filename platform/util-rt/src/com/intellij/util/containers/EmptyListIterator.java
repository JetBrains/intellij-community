// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import java.util.Collections;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * @deprecated Use {@link Collections#emptyListIterator()} instead
 */
@Deprecated
final class EmptyListIterator<E> extends EmptyIterator<E> implements ListIterator<E> {
  private EmptyListIterator() {
  }

  private static final EmptyListIterator<Object> INSTANCE = new EmptyListIterator<Object>();

  public static <E> EmptyListIterator<E> getInstance() {
    //noinspection unchecked
    return (EmptyListIterator<E>)INSTANCE;
  }

  @Override
  public boolean hasPrevious() {
    return false;
  }

  @Override
  public E previous() {
    throw new NoSuchElementException();
  }

  @Override
  public int nextIndex() {
    return 0;
  }

  @Override
  public int previousIndex() {
    return -1;
  }

  @Override
  public void set(E e) {
    throw new IllegalStateException();
  }

  @Override
  public void add(E e) {
    throw new UnsupportedOperationException();
  }
}
