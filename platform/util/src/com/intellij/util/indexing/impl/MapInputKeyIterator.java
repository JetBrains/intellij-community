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

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class MapInputKeyIterator<Key, Value> implements ForwardIndex.InputKeyIterator<Key,Value> {
  private final Map<Key, Value> myMap;
  private Iterator<Map.Entry<Key, Value>> myIterator;
  private Value myCurrentValue;

  public MapInputKeyIterator(Map<Key, Value> map) {
    myMap = map;
  }

  @Override
  public boolean isAssociatedValueEqual(@Nullable Value value) {
    return Comparing.equal(myCurrentValue, value);
  }

  @Override
  public boolean hasNext() {
    init();
    return myIterator.hasNext();
  }

  @Override
  public Key next() {
    Map.Entry<Key, Value> entry = myIterator.next();
    myCurrentValue = entry.getValue();
    return entry.getKey();
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private void init() {
    if (myIterator == null) {
      myIterator = (myMap == null ? Collections.<Key, Value>emptyMap() : myMap).entrySet().iterator();
    }
  }
}
