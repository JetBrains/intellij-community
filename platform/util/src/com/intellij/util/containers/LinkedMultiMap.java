/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author Evgeny Gerashchenko
 */
public class LinkedMultiMap<K, V> extends MultiMap<K, V> {
  @NotNull
  @Override
  protected Map<K, Collection<V>> createMap() {
    return new LinkedHashMap<>(getEqualityPolicy());
  }

  @NotNull
  @Override
  protected Map<K, Collection<V>> createMap(int initialCapacity, float loadFactor) {
    return new LinkedHashMap<>(initialCapacity, loadFactor, getEqualityPolicy());
  }

  protected EqualityPolicy<K> getEqualityPolicy() {
    return (EqualityPolicy<K>)EqualityPolicy.CANONICAL;
  }
}
