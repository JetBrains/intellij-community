/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PeekableIteratorWrapper<T> implements PeekableIterator<T> {
  @NotNull private final Iterator<T> myIterator;
  private T myValue = null;
  private boolean myValidValue = false;

  public PeekableIteratorWrapper(@NotNull Iterator<T> iterator) {
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
