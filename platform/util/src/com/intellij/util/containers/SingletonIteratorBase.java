// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class SingletonIteratorBase<T> implements Iterator<T> {
  private boolean myVisited;

  @Override
  public final boolean hasNext() {
    return !myVisited;
  }

  @Override
  public final T next() {
    if (myVisited) {
      throw new NoSuchElementException();
    }
    myVisited = true;
    checkCoModification();
    return getElement();
  }

  protected abstract void checkCoModification();

  protected abstract T getElement();
}