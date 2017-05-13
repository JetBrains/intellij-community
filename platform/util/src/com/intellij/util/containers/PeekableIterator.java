/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
