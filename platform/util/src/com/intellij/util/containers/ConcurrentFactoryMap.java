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

import java.util.Map;

/**
 * @author peter
 */
public abstract class ConcurrentFactoryMap<T,V> extends FactoryMap<T,V> {

  private interface MapFactory {
    int DEFAULT_CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();

    MapFactory V8_MAP_FACTORY = new MapFactory() {
      public <T, V> Map<T, V> createMap() {
        return new ConcurrentHashMap<T,V>();
      }
    };
    MapFactory DEFAULT_MAP_FACTORY = new MapFactory() {
      public <T, V> Map<T, V> createMap() {
        return new java.util.concurrent.ConcurrentHashMap<T,V>(16, 0.75f, DEFAULT_CONCURRENCY_LEVEL);
      }
    };

    <T, V> Map<T, V> createMap();
  }

  private static final MapFactory ourMapFactory = SystemInfo.isOracleJvm || SystemInfo.isAppleJvm? MapFactory.V8_MAP_FACTORY : MapFactory.DEFAULT_MAP_FACTORY;

  @Override
  protected Map<T, V> createMap() {
    return ourMapFactory.createMap();
  }
}
