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
package com.intellij.vcs.log.data;

import com.intellij.util.Processor;
import com.intellij.util.io.PersistentMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class InMemoryMap<K, V> implements PersistentMap<K, V> {
  private final Map<K, V> myMap = new HashMap<>();

  @Override
  public V get(K key) throws IOException {
    return myMap.get(key);
  }

  @Override
  public void put(K key, V value) throws IOException {
    myMap.put(key, value);
  }

  @Override
  public boolean processKeys(Processor<K> processor) throws IOException {
    for (K key : myMap.keySet()) {
      if (!processor.process(key)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void force() {
  }

  @Override
  public void close() throws IOException {
  }

  @Override
  public void markDirty() throws IOException {
  }
}
