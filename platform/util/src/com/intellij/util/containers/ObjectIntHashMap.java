/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * return -1 instead of 0 if no such mapping exists
 */
public class ObjectIntHashMap<K> extends TObjectIntHashMap<K> {
  public ObjectIntHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public ObjectIntHashMap(@NotNull TObjectHashingStrategy<K> strategy) {
    super(strategy);
  }

  public ObjectIntHashMap(int initialCapacity, @NotNull TObjectHashingStrategy<K> strategy) {
    super(initialCapacity, strategy);
  }

  public ObjectIntHashMap() {
    super();
  }

  @Override
  public final int get(K key) {
    return get(key, -1);
  }

  public final int get(K key, int defaultValue) {
    int index = index(key);
    return index < 0 ? defaultValue : _values[index];
  }
}
