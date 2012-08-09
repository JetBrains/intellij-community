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

import java.util.Collections;
import java.util.Iterator;

/**
* @author peter
*/
public abstract class FlatteningIterator<Group, Value> implements Iterator<Value> {
  private final Iterator<Group> valuesIterator;
  private Iterator<Value> groupIterator;
  private Boolean hasNextCache;

  public FlatteningIterator(Iterator<Group> groups) {
    valuesIterator = groups;
    groupIterator = Collections.<Value>emptyList().iterator();
  }

  @Override
  public boolean hasNext() {
    if (hasNextCache != null) {
      return hasNextCache.booleanValue();
    }

    while (!groupIterator.hasNext() && valuesIterator.hasNext()) {
      groupIterator = createValueIterator(valuesIterator.next());
    }
    return hasNextCache = groupIterator.hasNext();
  }

  protected abstract Iterator<Value> createValueIterator(Group group);

  @Override
  public Value next() {
    if (!hasNext()) {
      throw new AssertionError();
    }
    hasNextCache = null;
    return groupIterator.next();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
