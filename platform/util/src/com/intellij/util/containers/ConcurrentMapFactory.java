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

public abstract class ConcurrentMapFactory {
  private static final int DEFAULT_CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();

  private ConcurrentMapFactory() {
  }

  protected abstract <T, V> ConcurrentMap<T, V> _createMap();
  protected abstract <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity);
  protected abstract <T, V> ConcurrentMap<T, V> _createMap(TObjectHashingStrategy<T> hashStrategy);
  protected abstract <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity, float loadFactor, int concurrencyLevel);
  protected abstract <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity, float loadFactor, int concurrencyLevel, TObjectHashingStrategy<T> hashStrategy);

  private static final ConcurrentMapFactory V8_MAP_FACTORY = new ConcurrentMapFactory() {
    protected <T, V> ConcurrentMap<T, V> _createMap() {
      return new ConcurrentHashMap<T,V>();
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity) {
      return new ConcurrentHashMap<T,V>(initialCapacity);
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(TObjectHashingStrategy<T> hashStrategy) {
      return new ConcurrentHashMap<T,V>(hashStrategy);
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
      return new ConcurrentHashMap<T,V>(initialCapacity, loadFactor, concurrencyLevel);
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity, float loadFactor, int concurrencyLevel, @NotNull TObjectHashingStrategy<T> hashingStrategy) {
      return new ConcurrentHashMap<T,V>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
    }
  };

  private static final ConcurrentMapFactory PLATFORM_MAP_FACTORY = new ConcurrentMapFactory() {
    protected <T, V> ConcurrentMap<T, V> _createMap() {
      return _createMap(16, 0.75f, DEFAULT_CONCURRENCY_LEVEL);
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity) {
      return new java.util.concurrent.ConcurrentHashMap<T,V>(initialCapacity);
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(TObjectHashingStrategy<T> hashStrategy) {
      // ignoring strategy parameter, because it is not supported by this implementation
      return _createMap();
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
      return new java.util.concurrent.ConcurrentHashMap<T,V>(initialCapacity, loadFactor, concurrencyLevel);
    }

    protected <T, V> ConcurrentMap<T, V> _createMap(int initialCapacity, float loadFactor, int concurrencyLevel, @NotNull TObjectHashingStrategy<T> hashingStrategy) {
      // ignoring strategy parameter, because it is not supported by this implementation
      return _createMap(initialCapacity, loadFactor, concurrencyLevel);
    }
  };

  private static final ConcurrentMapFactory DEFAULT_FACTORY = SystemInfo.isOracleJvm || SystemInfo.isAppleJvm? V8_MAP_FACTORY : PLATFORM_MAP_FACTORY;

  public static <T, V> ConcurrentMap<T,V> createMap() {
    return DEFAULT_FACTORY._createMap();
  }

  public static <T, V> ConcurrentMap<T,V> createMap(TObjectHashingStrategy<T> hashStrategy) {
    return DEFAULT_FACTORY._createMap(hashStrategy);
  }

  public static <T, V> ConcurrentMap<T,V> createMap(int initialCapacity) {
    return DEFAULT_FACTORY._createMap(initialCapacity);
  }

  public static <T, V> ConcurrentMap<T,V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel, TObjectHashingStrategy<T> hashStrategy) {
    return DEFAULT_FACTORY._createMap(initialCapacity, loadFactor, concurrencyLevel, hashStrategy);
  }

  public static <T, V> ConcurrentMap<T,V> createMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return DEFAULT_FACTORY._createMap(initialCapacity, loadFactor, concurrencyLevel);
  }

}
