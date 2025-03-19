// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.IncorrectOperationException;

public final class SingletonIterator<T> extends SingletonIteratorBase<T> {
  private final T myElement;

  public SingletonIterator(T element) {
    myElement = element;
  }

  @Override
  protected void checkCoModification() {
  }

  @Override
  protected T getElement() {
    return myElement;
  }

  @Override
  public void remove() {
    throw new IncorrectOperationException();
  }
}
