/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

// only for those who cannot return null
public class CacheOneStepIterator<T> implements Iterator<T> {
  private final Iterator<T> myProbableIterator;
  private T myPreCalculated;

  public CacheOneStepIterator(final Iterator<T> probableIterator) {
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
