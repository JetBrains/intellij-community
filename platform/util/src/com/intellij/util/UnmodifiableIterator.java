// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import java.util.Iterator;

public class UnmodifiableIterator<T> implements Iterator<T> {
  private final Iterator<? extends T> myOriginalIterator;

  public UnmodifiableIterator(final Iterator<? extends T> originalIterator) {
    myOriginalIterator = originalIterator;
  }

  @Override
  public boolean hasNext() {
    return myOriginalIterator.hasNext();
  }

  @Override
  public T next() {
    return myOriginalIterator.next();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
