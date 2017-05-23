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
package com.intellij.util.containers;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;

public class ConcurrentMostlySingularMultiMapTest extends TestCase {

  public void testReplace() {
    ConcurrentMostlySingularMultiMap<String,String> map = new ConcurrentMostlySingularMultiMap<>();
    List<String> xxxList = Collections.singletonList("xxx");
    List<String> empty = Collections.emptyList();
    String KEY = "key";
    boolean replaced = map.replace(KEY, empty, xxxList);
    assertTrue(replaced);
    assertEquals(1, map.size());
    assertEquals(xxxList, map.get(KEY));

    List<String> yyyList = Collections.singletonList("yyy");
    replaced = map.replace(KEY, xxxList, yyyList);
    assertTrue(replaced);
    assertEquals(1, map.size());
    assertEquals(yyyList, map.get(KEY));

    replaced = map.replace(KEY, yyyList, empty);
    assertTrue(replaced);
    assertEquals(0, map.size());
    assertEquals(empty, map.get(KEY));
  }

  public void testAddRemove() {
    ConcurrentMostlySingularMultiMap<String, String> map = new ConcurrentMostlySingularMultiMap<>();
    assertEquals(Collections.emptyList(), map.get("key"));
    assertEquals(Collections.emptyList(), map.get("multiKey"));

    map.add("key", "single");
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.emptyList(), map.get("multiKey"));

    map.add("multiKey", "multi1");
    assertEquals(Collections.singletonList("multi1"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));

    map.add("multiKey", "multi2");
    assertEquals(ContainerUtil.newArrayList("multi1", "multi2"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));

    map.add("multiKey", "multi3");
    assertEquals(ContainerUtil.newArrayList("multi1", "multi2", "multi3"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));

    map.remove("multiKey", "multi1");
    assertEquals(ContainerUtil.newArrayList("multi2", "multi3"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));

    for (int i = 4; i < 24; i++) {
      map.add("multiKey", "multi" + Integer.toString(i));
    }
    assertEquals(2 + 20, ContainerUtil.newArrayList(map.get("multiKey")).size());
    assertEquals(2 + 20, map.valuesForKey("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(1, map.valuesForKey("key"));

    for (int i = 23; i >= 4; i--) {
      map.remove("multiKey", "multi" + Integer.toString(i));
    }
    assertEquals(ContainerUtil.newArrayList("multi2", "multi3"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
  }
}
