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

import com.intellij.util.ConcurrencyUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Allow to reuse structurally equal objects to avoid memory being wasted on them. Objects are cached on weak references
 * and garbage-collected when not needed anymore.
 *
 * @author peter
 */
public class WeakInterner<T> {
  private final ConcurrentMap<T, T> myMap;

  public WeakInterner() {
    myMap = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
  }
  public WeakInterner(@NotNull TObjectHashingStrategy<T> strategy) {
    myMap = ContainerUtil.createConcurrentWeakKeyWeakValueMap(strategy);
  }

  @NotNull
  public T intern(@NotNull T name) {
    return ConcurrencyUtil.cacheOrGet(myMap, name, name);
  }

  public void clear() {
    myMap.clear();
  }

  @NotNull
  public Set<T> getValues() {
    return ContainerUtil.newTroveSet(myMap.values());
  }
}
