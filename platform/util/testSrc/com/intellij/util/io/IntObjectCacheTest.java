/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.util.containers.IntObjectCache;
import junit.framework.TestCase;

import java.util.HashSet;

public class IntObjectCacheTest extends TestCase {
  public void testResize() {
    final int SIZE = 4;
    final IntObjectCache<String> cache = new IntObjectCache<>(SIZE);
    cache.addDeletedPairsListener(new IntObjectCache.DeletedPairsListener() {
      @Override
      public void objectRemoved(int key, Object value) {
        if (cache.count() >= cache.size() ) {
          final int newSize = cache.size() * 2;
          cache.resize(newSize);
          assertEquals(newSize, cache.size());
          cache.cacheObject(key, (String)value);
        }
      }
    });

    final int count = SIZE * 2000;
    for (int i = 1; i <= count; i++) {
      cache.cacheObject(i, String.valueOf(i));
    }

    for (int i = 1; i <= count; i++) {
      assertEquals(String.valueOf(i), cache.tryKey(i));
    }
  }
  
  public void intCacheIterator2() {
      IntObjectCache<Integer> cache = new IntObjectCache<>(4);
      cache.cacheObject(0, 0);
      cache.cacheObject(1, 1);
      cache.cacheObject(2, 2);
      cache.cacheObject(4, 4);
      cache.cacheObject(3, 3);
      cache.tryKey(4);
      cache.cacheObject(4, 5);
      HashSet<Object> values = new HashSet<>();
      for (Object obj : cache) {
        values.add(obj);
      }
      assertFalse(values.contains(0));
      assertTrue(values.contains(1));
      assertTrue(values.contains(2));
      assertTrue(values.contains(3));
      assertTrue(values.contains(5));
      assertTrue(cache.get(4).equals(5));
    }
  
}
