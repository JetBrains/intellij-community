/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.util.ListIterator;
import java.util.NoSuchElementException;

public class EmptyListIterator<E> extends EmptyIterator<E> implements ListIterator<E> {
  private static final EmptyListIterator<Object> INSTANCE = new EmptyListIterator<Object>();

  public static <E> EmptyListIterator<E> getInstance() {
    //noinspection unchecked
    return (EmptyListIterator<E>)INSTANCE;
  }

  public boolean hasPrevious() {
    return false;
  }

  public E previous() {
    throw new NoSuchElementException();
  }

  public int nextIndex() {
    return 0;
  }

  public int previousIndex() {
    return -1;
  }

  public void set(E e) {
    throw new IllegalStateException();
  }

  public void add(E e) {
    throw new UnsupportedOperationException();
  }
}
