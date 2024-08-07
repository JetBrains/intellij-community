// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus;

import java.util.Iterator;

// only for those who cannot return null
@ApiStatus.Internal
public final class CacheOneStepIterator<T> implements Iterator<T> {
  private final Iterator<? extends T> myProbableIterator;
  private T myPreCalculated;

  public CacheOneStepIterator(final Iterator<? extends T> probableIterator) {
    myProbableIterator = probableIterator;
    step();
  }

  private void step() {
    if (! myProbableIterator.hasNext()) {
      myPreCalculated = null;
    } else {
      myPreCalculated = myProbableIterator.next();
    }
  }

  @Override
  public boolean hasNext() {
    return myPreCalculated != null;
  }

  @Override
  public T next() {
    final T result = myPreCalculated;
    step();
    return result;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
