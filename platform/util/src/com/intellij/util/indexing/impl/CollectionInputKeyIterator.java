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
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public class CollectionInputKeyIterator<Key, Value> implements ForwardIndex.InputKeyIterator<Key, Value> {
  private final Collection<Key> mySeq;
  private Iterator<Key> myIt;

  public CollectionInputKeyIterator(Collection<Key> seq) {
    mySeq = seq;
  }

  @Override
  public boolean isAssociatedValueEqual(@Nullable Value value) {
    return false;
  }

  @Override
  public boolean hasNext() {
    init();
    return myIt.hasNext();
  }

  @Override
  public Key next() {
    return myIt.next();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  public Collection<Key> getCollection() {
    return mySeq == null ? Collections.<Key>emptySet() : mySeq;
  }

  private void init() {
    if (myIt == null) {
      myIt = getCollection().iterator();
    }
  }
}
