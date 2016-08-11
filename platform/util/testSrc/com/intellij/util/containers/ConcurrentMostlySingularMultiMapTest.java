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
}
