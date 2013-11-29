/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

public interface ConcurrentMapFactory {
  int DEFAULT_CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();

  ConcurrentMapFactory V8_MAP_FACTORY = new ConcurrentMapFactory() {
    public <T, V> ConcurrentMap<T, V> createMap() {
      return new ConcurrentHashMap<T,V>();
    }

    public <T, V> ConcurrentMap<T, V> createMap(int initialCapacity) {
      return new ConcurrentHashMap<T,V>(initialCapacity);
    }

    public <T, V> ConcurrentMap<T, V> createMap(TObjectHashingStrategy<T> hashStrategy) {
      return new ConcurrentHashMap<T,V>(hashStrategy);
    }

    public <T, V> ConcurrentMap<T, V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
      return new ConcurrentHashMap<T,V>(initialCapacity, loadFactor, concurrencyLevel);
    }

    public <T, V> ConcurrentMap<T, V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel, @NotNull TObjectHashingStrategy<T> hashingStrategy) {
      return new ConcurrentHashMap<T,V>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
    }
  };

  ConcurrentMapFactory PLATFORM_MAP_FACTORY = new ConcurrentMapFactory() {
    public <T, V> ConcurrentMap<T, V> createMap() {
      return createMap(16, 0.75f, DEFAULT_CONCURRENCY_LEVEL);
    }

    public <T, V> ConcurrentMap<T, V> createMap(int initialCapacity) {
      return new java.util.concurrent.ConcurrentHashMap<T,V>(initialCapacity);
    }

    public <T, V> ConcurrentMap<T, V> createMap(TObjectHashingStrategy<T> hashStrategy) {
      // ignoring strategy parameter, because it is not supported by this implementation
      return createMap();
    }

    public <T, V> ConcurrentMap<T, V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
      return new java.util.concurrent.ConcurrentHashMap<T,V>(initialCapacity, loadFactor, concurrencyLevel);
    }

    public <T, V> ConcurrentMap<T, V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel, @NotNull TObjectHashingStrategy<T> hashingStrategy) {
      // ignoring strategy parameter, because it is not supported by this implementation
      return createMap(initialCapacity, loadFactor, concurrencyLevel);
    }
  };

  ConcurrentMapFactory DEFAULT_FACTORY = SystemInfo.isOracleJvm || SystemInfo.isAppleJvm? V8_MAP_FACTORY : PLATFORM_MAP_FACTORY;

  <T, V> ConcurrentMap<T, V> createMap();
  <T, V> ConcurrentMap<T, V> createMap(int initialCapacity);
  <T, V> ConcurrentMap<T, V> createMap(TObjectHashingStrategy<T> hashStrategy);
  <T, V> ConcurrentMap<T, V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel);
  <T, V> ConcurrentMap<T, V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel, TObjectHashingStrategy<T> hashStrategy);
}
