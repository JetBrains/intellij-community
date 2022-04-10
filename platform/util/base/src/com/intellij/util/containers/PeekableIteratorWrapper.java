// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PeekableIteratorWrapper<T> implements PeekableIterator<T> {
  @NotNull private final Iterator<? extends T> myIterator;
  private T myValue = null;
  private boolean myValidValue = false;

  public PeekableIteratorWrapper(@NotNull Iterator<? extends T> iterator) {
    myIterator = iterator;
    advance();
  }

  @Override
  public boolean hasNext() {
    return myValidValue;
  }

  @Override
  public T next() {
    if (myValidValue) {
      T save = myValue;
      advance();
      return save;
    }
    throw new NoSuchElementException();
  }

  @Override
  public T peek() {
    if (myValidValue) {
      return myValue;
    }
    throw new NoSuchElementException();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private void advance() {
    myValidValue = myIterator.hasNext();
    myValue = myValidValue ? myIterator.next() : null;
  }
}
