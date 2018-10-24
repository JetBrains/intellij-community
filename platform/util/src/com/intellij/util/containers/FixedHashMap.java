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
package com.intellij.util.containers;

import java.util.*;

/**
 * {@link Map} which stores not more than {@link #maxSize} entries.
 * On attempt to put more, the eldest element is removed.
 */
public class FixedHashMap<K, V> extends LinkedHashMap<K, V> {
  private final int maxSize;

  public FixedHashMap(int maxSize) {
    this.maxSize = maxSize;
  }
  public FixedHashMap(int maxSize, int initialCapacity, float loadFactor, boolean accessOrder) {
    super(initialCapacity, loadFactor, accessOrder);
    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maxSize;
  }
}