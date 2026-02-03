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
package com.intellij.util;

import junit.framework.TestCase;

public class IntSLRUCacheTest extends TestCase {

  private static IntSLRUCache<String> createCache(int prot, int prob) {
    return new IntSLRUCache<>(prot, prob);
  }

  public void testCachedAfterAdd() {
    IntSLRUCache<String> cache = createCache(1, 1);
    cache.cacheEntry(0, "0");
    assert cache.getCachedEntry(0) != null;
    assert cache.getCachedEntry(1) == null;
  }

  public void testEvictInfrequent() {
    IntSLRUCache<String> cache = createCache(2, 2);

    cache.cacheEntry(0, "0");
    cache.cacheEntry(1, "1");
    cache.cacheEntry(2, "2");
    assert cache.getCachedEntry(0) == null;
    assert cache.getCachedEntry(1) != null;
    assert cache.getCachedEntry(2) != null;
  }

  public void testFrequentlyAccessedShouldSurvive() {
    IntSLRUCache<String> cache = createCache(2, 2);
    cache.cacheEntry(0, "0");
    cache.cacheEntry(1, "1");
    assert cache.getCachedEntry(0) != null;

    cache.cacheEntry(2, "2");
    cache.cacheEntry(3, "3");

    assert cache.getCachedEntry(0) != null;
    assert cache.getCachedEntry(1) == null;
    assert cache.getCachedEntry(2) != null;
  }

  public void testDropFrequentlyAccessedInThePast() {
    IntSLRUCache<String> cache = createCache(1, 1);
    cache.cacheEntry(0, "0");
    assert cache.getCachedEntry(0) != null; // protected now

    cache.cacheEntry(1, "1");
    cache.cacheEntry(2, "2");

    assert cache.getCachedEntry(0) != null; // still protected
    assert cache.getCachedEntry(1) == null;
    assert cache.getCachedEntry(2) != null; // protected now instead of 0

    cache.cacheEntry(1, "1");

    assert cache.getCachedEntry(0) == null;
    assert cache.getCachedEntry(2) != null;
    assert cache.getCachedEntry(1) != null;
  }

  public void testChangingWorkingSet() {
    IntSLRUCache<String> cache = createCache(1, 1);
    cache.cacheEntry(0, "0");
    assert cache.getCachedEntry(0) != null;

    cache.cacheEntry(1, "1");
    assert cache.getCachedEntry(1) != null;
    assert cache.getCachedEntry(0) != null;
    assert cache.getCachedEntry(1) != null;
    assert cache.getCachedEntry(0) != null;

    cache.cacheEntry(2, "2");
    assert cache.getCachedEntry(2) != null;
    assert cache.getCachedEntry(0) != null;
    assert cache.getCachedEntry(1) == null;
  }
}
