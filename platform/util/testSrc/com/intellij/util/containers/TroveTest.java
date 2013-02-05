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


import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Map;

public class TroveTest extends TestCase {
  public void testObjectInt() {
    TObjectIntHashMap<String> map = new TObjectIntHashMap<String>();
    map.trimToSize();
    for (int i = 0; i < 100; i++) {
      String key = String.valueOf(i);
      map.put(key, i);
      map.put(key+"a", i);
    }
    for (int i = 0; i < 100; i++) {
      String key = String.valueOf(i);
      Assert.assertEquals(i, map.get(key));
      Assert.assertEquals(i, map.get(key+"a"));
    }
    Assert.assertEquals(200, map.size());
  }

  public void testTHashMap_Entry() {
    Map<Object, Object> map = new THashMap<Object, Object>();
    map.put("1", "2");

    Map.Entry<Object, Object> entry = map.entrySet().iterator().next();

    assertEquals("1", entry.getKey());
    assertEquals("2", entry.getValue());
    Object old = entry.setValue("3");
    assertEquals("2", old);
    assertEquals("1", entry.getKey());
    assertEquals("3", entry.getValue());

    Map.Entry<Object, Object> refreshed = map.entrySet().iterator().next();
    assertEquals("1", refreshed.getKey());
    assertEquals("3", refreshed.getValue());
  }
}
