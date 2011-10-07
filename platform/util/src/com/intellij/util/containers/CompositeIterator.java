/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 9/28/11
 * Time: 12:38 PM
 */
public class CompositeIterator<Key> implements Iterator<Key> {
  private int myPreviousIdx;
  private int myIdx;
  private final List<Iterator<Key>> myIterators;

  public CompositeIterator(final List<Iterator<Key>> iterators) {
    myIterators = iterators;
    myIdx = -1;
    myPreviousIdx = -1;
    for (int i = 0; i < myIterators.size(); i++) {
      final Iterator<Key> iterator = myIterators.get(i);
      if (iterator.hasNext()) {
        myIdx = i;
        break;
      }
    }
  }

  @Override
  public boolean hasNext() {
    return (myIdx >= 0) && myIterators.get(myIdx).hasNext();
  }

  @Override
  public Key next() {
    final Key result = myIterators.get(myIdx).next();
    recalculateCurrent();
    return result;
  }

  private void recalculateCurrent() {
    if (myIdx == -1) return;
    if (! myIterators.get(myIdx).hasNext()) {
      myPreviousIdx = myIdx;
      myIdx = -1;
      for (int i = myPreviousIdx; i < myIterators.size(); i++) {
        final Iterator<Key> iterator = myIterators.get(i);
        if (iterator.hasNext()) {
          myIdx = i;
          break;
        }
      }
    }
  }

  @Override
  public void remove() {
    if ((myPreviousIdx != -1) && (myPreviousIdx != myIdx)) {
      // last element
      final Iterator<Key> keyIterator = myIterators.get(myPreviousIdx);
      keyIterator.remove(); // already on last position
    } else {
      myIterators.get(myIdx).remove();
    }
    recalculateCurrent();
  }
}
