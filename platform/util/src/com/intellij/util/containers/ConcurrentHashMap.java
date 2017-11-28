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

import java.util.Map;

/**
 * @deprecated Use {@link ContainerUtil#newConcurrentMap()} instead.
 * TODO to remove in IDEA 2018.1
 */
public final class ConcurrentHashMap<K, V> extends java.util.concurrent.ConcurrentHashMap<K, V> {
  public ConcurrentHashMap() {
  }

  public ConcurrentHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
    super(m);
  }

  public ConcurrentHashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    super(initialCapacity, loadFactor, concurrencyLevel);
  }
}