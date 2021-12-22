// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator with additional ability to {@link #peek()} the current element without moving the cursor.
 */
public interface PeekableIterator<T> extends Iterator<T> {
  /**
   * @return the current element.
   * Upon iterator creation should return the first element.
   * After {@link #hasNext()} returned false might throw {@link NoSuchElementException}.
   */
  T peek() throws NoSuchElementException;

  PeekableIterator EMPTY = new PeekableIterator() {
    @Override
    public Object peek() {
      throw new NoSuchElementException();
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Object next() {
      return null;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }
  };
}
