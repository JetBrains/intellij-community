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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MostlySingularMultiMap}.
 */
public class MostlySingularMultiMapTest extends TestCase {
  public void testAddRemove() {
    MostlySingularMultiMap<String, String> map = new MostlySingularMultiMap<>();
    assertEquals(Collections.emptyList(), map.get("key"));
    assertEquals(Collections.emptyList(), map.get("otherKey"));
    assertEquals(Collections.emptyList(), map.get("multiKey"));

    map.add("key", "single");
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.emptyList(), map.get("otherKey"));
    assertEquals(Collections.emptyList(), map.get("multiKey"));

    map.add("otherKey", "single");
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.emptyList(), map.get("multiKey"));

    map.add("multiKey", "multi1");
    assertEquals(Collections.singletonList("multi1"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));

    map.add("multiKey", "multi2");
    assertEquals(List.of("multi1", "multi2"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));

    map.add("multiKey", "multi3");
    assertEquals(List.of("multi1", "multi2", "multi3"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));

    map.remove("multiKey", "multi1");
    assertEquals(List.of("multi2", "multi3"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));

    for (int i = 4; i < 24; i++) {
      map.add("multiKey", "multi" + i);
    }
    assertEquals(2 + 20, ContainerUtil.newArrayList(map.get("multiKey")).size());
    assertEquals(2 + 20, map.valuesForKey("multiKey"));
    assertThat(ContainerUtil.newArrayList(map.get("multiKey"))).contains("multi10");
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(1, map.valuesForKey("key"));
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));
    assertEquals(1, map.valuesForKey("otherKey"));

    for (int i = 23; i >= 4; i--) {
      map.remove("multiKey", "multi" + i);
    }
    assertEquals(List.of("multi2", "multi3"), map.get("multiKey"));
    assertEquals(Collections.singletonList("single"), map.get("key"));
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));

    assertTrue(map.removeAllValues("key"));
    assertEquals(Collections.emptyList(), map.get("key"));
    assertEquals(Collections.singletonList("single"), map.get("otherKey"));
    assertEquals(List.of("multi2", "multi3"), map.get("multiKey"));
    assertFalse(map.removeAllValues("key"));

    assertTrue(map.removeAllValues("otherKey"));
    assertEquals(Collections.emptyList(), map.get("key"));
    assertEquals(Collections.emptyList(), map.get("otherKey"));
    assertEquals(List.of("multi2", "multi3"), map.get("multiKey"));

    assertTrue(map.removeAllValues("multiKey"));
    assertEquals(Collections.emptyList(), map.get("key"));
    assertEquals(Collections.emptyList(), map.get("otherKey"));
    assertEquals(Collections.emptyList(), map.get("multiKey"));
  }

  public void testAddAll() {
    MostlySingularMultiMap<String, String> map1 = new MostlySingularMultiMap<>();
    MostlySingularMultiMap<String, String> map2 = new MostlySingularMultiMap<>();

    map1.addAll(map2);
    assertEquals(0, map1.size());
    assertEquals(0, map2.size());

    map1.add("k1a", "x");
    map1.add("k1b", "x");
    map1.add("k1c", "x");
    map1.add("k1d", "x");

    map1.add("k2a", "x"); map1.add("k2a", "y");
    map1.add("k2b", "x"); map1.add("k2b", "y");
    map1.add("k2c", "x"); map1.add("k2c", "y");
    map1.add("k2d", "x"); map1.add("k2d", "y");

    map1.add("k3a", "x"); map1.add("k3a", "y"); map1.add("k3a", "z");
    map1.add("k3b", "x"); map1.add("k3b", "y"); map1.add("k3b", "z");
    map1.add("k3c", "x"); map1.add("k3c", "y"); map1.add("k3c", "z");
    map1.add("k3d", "x"); map1.add("k3d", "y"); map1.add("k3d", "z");

    map2.add("k0a", "x"); map2.add("k0a", "y"); map2.add("k0a", "z");
    map2.add("k0b", "x"); map2.add("k0b", "y");
    map2.add("k0c", "x");

    map2.add("k1a", "y"); map2.add("k1a", "z"); map2.add("k1a", "w");
    map2.add("k1b", "y"); map2.add("k1b", "z");
    map2.add("k1c", "y");

    map2.add("k2a", "a"); map2.add("k2a", "b"); map2.add("k2a", "c");
    map2.add("k2b", "a"); map2.add("k2b", "b");
    map2.add("k2c", "a");

    map2.add("k3a", "a"); map2.add("k3a", "b"); map2.add("k3a", "c");
    map2.add("k3b", "a"); map2.add("k3b", "b");
    map2.add("k3c", "a");

    map1.addAll(map2);

    assertEquals(List.of("x", "y", "z"),
                 map1.get("k0a"));
    assertEquals(List.of("x", "y"),
                 map1.get("k0b"));
    assertEquals(List.of("x"),
                 map1.get("k0c"));

    assertEquals(List.of("x", "y", "z", "w"),
                 map1.get("k1a"));
    assertEquals(List.of("x", "y", "z"),
                 map1.get("k1b"));
    assertEquals(List.of("x", "y"),
                 map1.get("k1c"));
    assertEquals(List.of("x"),
                 map1.get("k1d"));

    assertEquals(List.of("x", "y", "a", "b", "c"),
                 map1.get("k2a"));
    assertEquals(List.of("x", "y", "a", "b"),
                 map1.get("k2b"));
    assertEquals(List.of("x", "y", "a"),
                 map1.get("k2c"));
    assertEquals(List.of("x", "y"),
                 map1.get("k2d"));

    assertEquals(List.of("x", "y", "z", "a", "b", "c"),
                 map1.get("k3a"));
    assertEquals(List.of("x", "y", "z", "a", "b"),
                 map1.get("k3b"));
    assertEquals(List.of("x", "y", "z", "a"),
                 map1.get("k3c"));
    assertEquals(List.of("x", "y", "z"),
                 map1.get("k3d"));
  }

  public void testAddAllDoesntOwnOriginalValues() {
    MostlySingularMultiMap<String, String> mapEmpty = new MostlySingularMultiMap<>();
    MostlySingularMultiMap<String, String> mapWithOne = new MostlySingularMultiMap<>();
    MostlySingularMultiMap<String, String> mapWithMultiple = new MostlySingularMultiMap<>();
    
    mapWithOne.add("1", "a");
    
    mapWithMultiple.add("1", "a");
    mapWithMultiple.add("1", "b");
    
    MostlySingularMultiMap<String, String> from = new MostlySingularMultiMap<>();
    from.add("1", "x");
    from.add("1", "y");
    
    mapEmpty.addAll(from);
    mapWithOne.addAll(from);
    mapWithMultiple.addAll(from);
    
    mapEmpty.add("1", "mapEmpty");
    mapWithOne.add("1", "mapWithOne");
    mapWithMultiple.add("1", "mapWithMultiple");
    
    from.add("1", "from");

    assertEquals(Arrays.asList("x", "y", "mapEmpty"), ContainerUtil.newArrayList(mapEmpty.get("1")));
    assertEquals(Arrays.asList("a", "x", "y", "mapWithOne"), ContainerUtil.newArrayList(mapWithOne.get("1")));
    assertEquals(Arrays.asList("a", "b", "x", "y", "mapWithMultiple"), ContainerUtil.newArrayList(mapWithMultiple.get("1")));
    
    assertEquals(Arrays.asList("x", "y", "from"), ContainerUtil.newArrayList(from.get("1")));
  }
}
