/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import junit.framework.TestCase;

public class KeyFMapTest extends TestCase {
  private final static Key[] KEYS = {
    Key.create("K0"),
    Key.create("K1"),
    Key.create("K2"),
    Key.create("K3"),
    Key.create("K4"),
    Key.create("K5"),
    Key.create("K6"),
    Key.create("K7"),
    Key.create("K8"),
    Key.create("K9")
  };

  private final static Object[] VALUES = { "V0", "V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9" };

  private static KeyFMap createKeyFMap(Key[] keys, Object[] values, int size) {
    KeyFMap fmap = KeyFMap.EMPTY_MAP;

    for (int i = 0; i < size; i++) {
      fmap = fmap.plus(keys[i], values[i]);
    }

    return fmap;
  }

  private static void doTestGetKeys(int size) {
    KeyFMap map = createKeyFMap(KEYS, VALUES, size);

    Key[] keys = map.getKeys();

    assertEquals(size, keys.length);

    for (int i = 0; i < size; i++) {
      int index = ArrayUtil.indexOf(keys, KEYS[i], 0, size);
      assertTrue("Key" + " not found: " + KEYS[i], index >= 0);
    }
  }

  public void testGetKeysOnEmptyFMap() {
    doTestGetKeys(0);
  }

  public void testGetKeysOnOneElementFMap() {
    doTestGetKeys(1);
  }

  public void testGetKeysOnTwoElementFMap() {
    doTestGetKeys(2);
  }

  public void testGetKeysOnArrayBackedFMap() {
    doTestGetKeys(5);
  }

  public void testGetKeysOnMapBackedFMap() {
    doTestGetKeys(10);
  }
}
