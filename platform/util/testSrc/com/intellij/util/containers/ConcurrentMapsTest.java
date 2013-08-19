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

import gnu.trove.TObjectHashingStrategy;
import junit.framework.TestCase;

import java.util.Map;
import java.util.Set;

public class ConcurrentMapsTest extends TestCase {
  public void testKeysRemovedWhenIdentityStrategyIsUsed() {
    ConcurrentWeakHashMap<Object, Object> map = new ConcurrentWeakHashMap<Object, Object>(TObjectHashingStrategy.IDENTITY);
    map.put(new Object(), new Object());

    do {
      System.gc();
    }
    while (!map.processQueue());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  public void testRemoveFromEntrySet() {
    ConcurrentSoftHashMap<Object, Object> map = new ConcurrentSoftHashMap<Object, Object>();
    map.put(this, this);
    Set<Map.Entry<Object,Object>> entries = map.entrySet();
    assertEquals(1, entries.size());
    Map.Entry<Object, Object> entry = entries.iterator().next();
    entries.remove(entry);

    assertTrue(map.isEmpty());
  }
}
