/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.indexing.containers;

import com.intellij.util.indexing.ValueContainer;
import gnu.trove.TIntHashSet;

public class IdSet extends TIntHashSet implements RandomAccessIntContainer {
  public IdSet(final int initialCapacity) {
    super(initialCapacity, 0.98f);
  }

  @Override
  public void compact() {
    if (((int)(capacity() * _loadFactor)/ Math.max(1, size())) >= 3) {
      super.compact();
    }
  }

  @Override
  public RandomAccessIntContainer ensureContainerCapacity(int diff) {
    return this; // todo
  }

  @Override
  public ValueContainer.IntPredicate intPredicate() {
    return new ValueContainer.IntPredicate() {
      @Override
      public boolean contains(int id) {
        return IdSet.this.contains(id);
      }
    };
  }

  @Override
  public IntIdsIterator intIterator() {
    return new IntSetIterator();
  }

  private class IntSetIterator extends TroveSetIntIterator {
    IntSetIterator() {
      super(IdSet.this);
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new IntSetIterator();
    }
  }
}
