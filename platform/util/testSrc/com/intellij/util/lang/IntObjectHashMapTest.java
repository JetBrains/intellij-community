/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.lang;

import gnu.trove.TIntObjectHashMap;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class IntObjectHashMapTest {
  @Test
  public void test() {
    IntObjectHashMap map = new IntObjectHashMap();
    TIntObjectHashMap<Object> checkMap = new TIntObjectHashMap<>();
    TIntObjectHashMap<Object> dupesMap = new TIntObjectHashMap<>();
    Random random = new Random();
    for(int i = 0; i < 1000000; ++i) {
      int key = random.nextInt();
      String value = String.valueOf(random.nextInt());

      if (!checkMap.contains(key)) {
        map.put(key, value);
        checkMap.put(key, value);
        assertEquals(map.size(), checkMap.size());
        assertEquals(value, map.get(key));
      }
      else {
        dupesMap.put(key, value);
      }
    }

    dupesMap.put(0, "random string");

    dupesMap.forEachEntry((int k, Object v) -> {
      checkMap.put(k, v);
      map.put(k, v);
      assertEquals(map.size(), checkMap.size());
      assertEquals(v, map.get(k));
      return true;
    });

    String value = "random string2";
    checkMap.put(0, value);
    map.put(0, value);

    checkMap.forEachEntry((k, v) -> {
      assertEquals(v, map.get(k));
      return true;
    });
    assertEquals(map.size(), checkMap.size());
  }
}