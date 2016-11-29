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
package com.intellij.util.containers.hash;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class LinkedHashMapTest {

  @Test
  public void testPutGet() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 1000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.get(i));
    }
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.put(i, Integer.toString(i + 1)));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i + 1), tested.get(i));
    }
  }

  @Test
  public void testPutGet2() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 1000; ++i) {
      tested.put(i - 500, Integer.toString(i));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.get(i - 500));
    }
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.put(i - 500, Integer.toString(i + 1)));
    }
    assertEquals(1000, tested.size());
    for (int i = 0; i < 1000; ++i) {
      assertEquals(Integer.toString(i + 1), tested.get(i - 500));
    }
  }

  @Test
  public void testPutGetRemove() {
     final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
     for (int i = 0; i < 1000; ++i) {
       tested.put(i, Integer.toString(i));
     }
     assertEquals(1000, tested.size());
     for (int i = 0; i < 1000; i += 2) {
       assertEquals(Integer.toString(i), tested.remove(i));
     }
     assertEquals(500, tested.size());
     for (int i = 0; i < 1000; ++i) {
       assertEquals((i % 2 == 0) ? null : Integer.toString(i), tested.get(i));
     }
  }

  @Test
  public void keySet() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 10000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    int i = 0;
    for (Integer key : tested.keySet()) {
      assertEquals(i++, key.intValue());
    }
  }

  @Test
  public void keySet2() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();
    for (int i = 0; i < 10000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    Iterator<Integer> it = tested.keySet().iterator();
    while (it.hasNext()) {
      final int i = it.next();
      if (i % 2 == 0) {
        it.remove();
      }
    }

    assertEquals(5000, tested.size());
    it = tested.keySet().iterator();
    for (int i = 1; i <= 9999; i += 2) {
      Assert.assertTrue(it.hasNext());
      assertEquals(i, it.next().intValue());
    }
  }

  @Test
  public void lru() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<Integer, String>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
        return size() > 500;
      }
    };
    for (int i = 0; i < 1000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    assertEquals(500, tested.size());
    for (int i = 0; i < 500; ++i) {
      Assert.assertNull(tested.remove(i));
    }
    assertEquals(500, tested.size());
    for (int i = 500; i < 1000; ++i) {
      assertEquals(Integer.toString(i), tested.remove(i));
    }
    assertEquals(0, tested.size());
  }

  @Test
  public void lru2() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<Integer, String>(0, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
        return size() > 1000;
      }
    };
    for (int i = 0; i < 1000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    assertEquals(Integer.toString(0), tested.get(0));
    for (int i = 1000; i < 1999; ++i) {
      tested.put(i, Integer.toString(i));
    }
    assertEquals(Integer.toString(0), tested.get(0));
    tested.put(2000, Integer.toString(2000));
    Assert.assertNull(tested.get(1000));
  }

  @Test
  public void lru3() {
    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<Integer, String>(0, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
        return size() > 1000;
      }
    };
    for (int i = 0; i < 1000; ++i) {
      tested.put(i, Integer.toString(i));
    }
    assertEquals(Integer.toString(999), tested.remove(999));
    assertEquals(999, tested.size());
    assertEquals(Integer.toString(0), tested.get(0));
    for (int i = 1000; i < 1999; ++i) {
      tested.put(i, Integer.toString(i));
    }
    assertEquals(Integer.toString(0), tested.get(0));
    tested.put(2000, Integer.toString(2000));
    assertNull(tested.get(1000));
  }

  @Test
  public void valuesIteration() {
    Map<Integer, String> map = new LinkedHashMap<>();
    map.put(1, "a");
    map.put(2, "b");
    map.put(3, "c");
    Iterator<String> iterator = map.values().iterator();
    assertEquals("a", iterator.next());
    assertEquals("b", iterator.next());
    assertEquals("c", iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Test
  public void accessOrderValuesIteration() {
    Map<Integer, String> map = new LinkedHashMap<>(0, true);
    map.put(1, "a");
    map.put(2, "b");
    map.put(3, "c");
    Iterator<String> iterator = map.values().iterator();
    assertEquals("a", iterator.next());
    assertEquals("b", iterator.next());
    assertEquals("c", iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Test
  public void lastAddedKey() {
    LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
    map.put(1, "a");
    map.put(2, "b");
    map.put(3, "c");
    map.get(1);
    map.get(2);
    assertEquals(3, map.getLastKey().intValue());
    assertEquals("c", map.getLastValue());
    map.remove(2);
    assertEquals(3, map.getLastKey().intValue());
    assertEquals("c", map.getLastValue());
    map.remove(3);
    assertEquals(1, map.getLastKey().intValue());
    assertEquals("a", map.getLastValue());
    map.remove(1);
    assertNull(map.getLastKey());
    assertNull(map.getLastValue());
  }

  //@Test

  public void benchmarkGet() {


    long started;


    final Map<Integer, String> map = new java.util.LinkedHashMap<>();

    for (int i = 0; i < 100000; ++i) {

      map.put(i, Integer.toString(i));

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        map.get(j);

      }

    }

    System.out.println("100 000 000 lookups in java.util.LinkedHashMap took " + (System.currentTimeMillis() - started));


    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();

    for (int i = 0; i < 100000; ++i) {

      tested.put(i, Integer.toString(i));

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        tested.get(j);

      }

    }

    System.out.println("100 000 000 lookups in LinkedHashMap took " + (System.currentTimeMillis() - started));

  }


  //@Test

  public void benchmarkGetMissingKeys() {


    long started;


    final Map<Integer, String> map = new java.util.LinkedHashMap<>();

    for (int i = 0; i < 100000; ++i) {

      map.put(i, Integer.toString(i));

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        map.get(j + 1000000);

      }

    }

    System.out.println("100 000 000 lookups in java.util.LinkedHashMap took " + (System.currentTimeMillis() - started));


    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();

    for (int i = 0; i < 100000; ++i) {

      tested.put(i, Integer.toString(i));

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        tested.get(j + 1000000);

      }

    }

    System.out.println("100 000 000 lookups in LinkedHashMap took " + (System.currentTimeMillis() - started));

  }


  //@Test

  public void benchmarkLRU() {


    long started;


    final Map<Integer, String> map = new java.util.LinkedHashMap<>();

    for (int i = 0; i < 100000; ++i) {

      map.put(i, Integer.toString(i));

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 200; ++i) {

      for (int j = 0; j < 100000; ++j) {

        final String v = map.remove(j);

        map.put(j, v);

      }

    }

    System.out.println("20 000 000 LRU lookups in java.util.LinkedHashMap took " + (System.currentTimeMillis() - started));


    final LinkedHashMap<Integer, String> tested = new LinkedHashMap<>();

    for (int i = 0; i < 100000; ++i) {

      tested.put(i, Integer.toString(i));

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 200; ++i) {

      for (int j = 0; j < 100000; ++j) {

        tested.get(j);

      }

    }

    System.out.println("20 000 000 lookups in LinkedHashMap took " + (System.currentTimeMillis() - started));

  }

}

