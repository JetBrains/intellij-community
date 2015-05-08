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
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.List;

public class KeyFMapTest extends TestCase {
  private static KeyFMap createKeyFMap(List<Key> keys, List<Object> values) {
    KeyFMap map = KeyFMap.EMPTY_MAP;

    for (int i = 0; i < keys.size(); i++) {
      map = map.plus(keys.get(i), values.get(i));
    }

    return map;
  }

  private static void doTestGetKeys(int size) {
    List<Key> keys = ContainerUtil.newArrayList();
    List<Object> values = ContainerUtil.newArrayList();
    for (int i = 0; i < size; i++) {
      keys.add(Key.create("Key#" + i));
      values.add("Value#" + i);
    }

    KeyFMap map = createKeyFMap(keys, values);

    Key[] actualKeys = map.getKeys();

    assertEquals(size, actualKeys.length);

    for (Key key : keys) {
      assertTrue("Key not found: " + key, ArrayUtil.contains(key, actualKeys));
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
    doTestGetKeys(15);
  }
}