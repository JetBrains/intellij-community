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

package com.intellij.util.containers;

import junit.framework.Assert;
import junit.framework.TestCase;import java.util.HashMap;
import java.util.HashSet;

/**
 * @author lvo
 */
public class ObjectCacheTest extends TestCase {
  public void testCacheFiniteness() {
    ObjectCache<String, String> cache = new ObjectCache<>(4);
    cache.put("Eclipse", "Another IDE");
    cache.put("IDEA", "good");
    cache.put("IDEA 4.5", "better");
    cache.put("IDEA 5.0", "perfect");
    cache.put("IDEA 6.0", "ideal");
    // "Eclipse" should already leave the cache
    Assert.assertNull(cache.get("Eclipse"));
  }

  public void testCacheIterator() {
    ObjectCache<String, String> cache = new ObjectCache<>(4);
    cache.put("Eclipse", "Another IDE");
    cache.put("IDEA", "good IDEA");
    cache.put("IDEA 4.5", "better IDEA");
    cache.put("IDEA 5.0", "perfect IDEA");
    cache.put("IDEA 6.0", "IDEAL");
    java.util.HashSet<String> values = new java.util.HashSet<>();
    for (Object obj : cache) {
      values.add((String)obj);
    }
    Assert.assertNull(cache.get("Eclipse"));
    Assert.assertFalse(values.contains("Another IDE"));
    Assert.assertTrue(values.contains("good IDEA"));
    Assert.assertTrue(values.contains("better IDEA"));
    Assert.assertTrue(values.contains("perfect IDEA"));
    Assert.assertTrue(values.contains("IDEAL"));
  }

  final private static HashMap removedPairs = new java.util.HashMap();

  private static class CacheDeletedPairsListener implements ObjectCache.DeletedPairsListener {
    @Override
    public void objectRemoved(Object key, Object value) {
      removedPairs.put(key, value);
    }
  }

  public void testCacheListeners() {
    ObjectCache<String, String> cache = new ObjectCache<>(4);
    cache.addDeletedPairsListener(new CacheDeletedPairsListener());
    removedPairs.clear();
    cache.put("Eclipse", "Another IDE");
    cache.put("Eclipses", "Another IDEs");
    cache.put("IDEA", "good IDEA");
    cache.put("IDEA 4.5", "better IDEA");
    cache.put("IDEA 5.0", "perfect IDEA");
    cache.put("IDEA 6.0", "IDEAL");
    Assert.assertEquals("Another IDE", removedPairs.get("Eclipse"));
    Assert.assertEquals("Another IDEs", removedPairs.get("Eclipses"));
  }

  public void testIntCacheFiniteness() {
    IntObjectCache<Integer> cache = new IntObjectCache<>(4);
    cache.put(0, 0);
    cache.put(1, 1);
    cache.put(2, 2);
    cache.put(3, 3);
    cache.put(4, 4);
    // 0 should already leave the cache
    Assert.assertNull(cache.tryKey(0));
  }

  public void testIntCacheIterator() {
    IntObjectCache<Integer> cache = new IntObjectCache<>(4);
    cache.put(0, 0);
    cache.put(1, 1);
    cache.put(2, 2);
    cache.put(3, 3);
    cache.put(4, 4);
    java.util.HashSet<Object> values = new HashSet<>();
    for (Object obj : cache) {
      values.add(obj);
    }
    Assert.assertFalse(values.contains(0));
    Assert.assertTrue(values.contains(1));
    Assert.assertTrue(values.contains(2));
    Assert.assertTrue(values.contains(3));
    Assert.assertTrue(values.contains(4));
  }

  public void testIntCacheNegativeKeys() {
    IntObjectCache<Object> cache = new IntObjectCache<>(8);
    cache.put(-1, 1);
    cache.put(-2, 2);
    cache.put(-3, 3);
    cache.put(-4, 4);
    cache.put(1, 1);
    cache.put(2, 2);
    cache.put(3, 3);
    cache.put(4, 4);
    Assert.assertNull(cache.tryKey(0));
    Assert.assertNotNull(cache.tryKey(-1));
    Assert.assertNotNull(cache.tryKey(-2));
    Assert.assertNotNull(cache.tryKey(-3));
    Assert.assertNotNull(cache.tryKey(-4));
  }
}
