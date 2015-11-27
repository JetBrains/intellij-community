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

import org.jetbrains.annotations.NotNull;

/**
 * An LRU cache with synchronization around the primary cache operations (get() and insertion
 * of a newly created value). Other map operations are not synchronized.
 */
public abstract class SynchronizedSLRUCache<K, V> extends SLRUMap<K,V> {
  protected final Object myLock = new Object();

  protected SynchronizedSLRUCache(final int protectedQueueSize, final int probationalQueueSize) {
    super(protectedQueueSize, probationalQueueSize);
  }

  @NotNull
  public abstract V createValue(K key);

  @Override
  @NotNull
  public V get(K key) {
    V value;
    synchronized (myLock) {
      value = super.get(key);
      if (value != null) {
        return value;
      }
    }
    value = createValue(key);
    synchronized (myLock) {
      put(key, value);
    }
    return value;
  }
}