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


import gnu.trove.THashMap;

import org.junit.Assert;

import org.junit.Test;


import java.util.HashSet;

import java.util.Iterator;

import java.util.Map;

import java.util.Set;


public class HashMapTest {


  @Test

  public void testPutGet() {

    final HashMap<Integer, String> tested = new HashMap<>();

    for (int i = 0; i < 1000; ++i) {

      tested.put(i, Integer.toString(i));
    }

    Assert.assertEquals(1000, tested.size());

    for (int i = 0; i < 1000; ++i) {

      Assert.assertEquals(Integer.toString(i), tested.get(i));
    }

    for (int i = 0; i < 1000; ++i) {

      Assert.assertEquals(Integer.toString(i), tested.put(i, Integer.toString(i + 1)));
    }

    Assert.assertEquals(1000, tested.size());

    for (int i = 0; i < 1000; ++i) {

      Assert.assertEquals(Integer.toString(i + 1), tested.get(i));
    }
  }


  @Test

  public void testPutGet2() {

    final HashMap<Integer, String> tested = new HashMap<>();

    for (int i = 0; i < 1000; ++i) {

      tested.put(i - 500, Integer.toString(i));
    }

    Assert.assertEquals(1000, tested.size());

    for (int i = 0; i < 1000; ++i) {

      Assert.assertEquals(Integer.toString(i), tested.get(i - 500));
    }

    for (int i = 0; i < 1000; ++i) {

      Assert.assertEquals(Integer.toString(i), tested.put(i - 500, Integer.toString(i + 1)));
    }

    Assert.assertEquals(1000, tested.size());

    for (int i = 0; i < 1000; ++i) {

      Assert.assertEquals(Integer.toString(i + 1), tested.get(i - 500));
    }
  }


  @Test

  public void testPutGetRemove() {

    final HashMap<Integer, String> tested = new HashMap<>();

    for (int i = 0; i < 1000; ++i) {

      tested.put(i, Integer.toString(i));
    }

    Assert.assertEquals(1000, tested.size());

    for (int i = 0; i < 1000; i += 2) {

      Assert.assertEquals(Integer.toString(i), tested.remove(i));
    }

    Assert.assertEquals(500, tested.size());

    for (int i = 0; i < 1000; ++i) {

      Assert.assertEquals((i % 2 == 0) ? null : Integer.toString(i), tested.get(i));
    }
  }


  @Test

  public void keySet() {

    final HashMap<Integer, String> tested = new HashMap<>();

    final Set<Integer> set = new HashSet<>();


    for (int i = 0; i < 10000; ++i) {

      tested.put(i, Integer.toString(i));

      set.add(i);
    }

    for (Integer key : tested.keySet()) {

      Assert.assertTrue(set.remove(key));
    }

    Assert.assertEquals(0, set.size());
  }


  @Test

  public void keySet2() {

    final HashMap<Integer, String> tested = new HashMap<>();

    final Set<Integer> set = new HashSet<>();


    for (int i = 0; i < 10000; ++i) {

      tested.put(i, Integer.toString(i));

      set.add(i);
    }

    Iterator<Integer> it = tested.keySet().iterator();

    while (it.hasNext()) {

      final int i = it.next();

      if (i % 2 == 0) {

        it.remove();

        Assert.assertTrue(set.remove(i));
      }
    }


    Assert.assertEquals(5000, tested.size());


    it = tested.keySet().iterator();

    for (int i = 9999; i > 0; i -= 2) {

      Assert.assertTrue(it.hasNext());

      Assert.assertTrue(it.next() % 2 != 0);

      Assert.assertTrue(set.remove(i));
    }

    Assert.assertEquals(0, set.size());
  }


  //@Test

  public void benchmarkGet() {


    long started;


    final Map<Integer, String> map = new java.util.HashMap<>();

    for (int i = 0; i < 100000; ++i) {

      map.put(i, Integer.toString(i));
    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        map.get(j);
      }
    }

    System.out.println("100 000 000 lookups in java.util.HashMap took " + (System.currentTimeMillis() - started));


    final Map<Integer, String> troveMap = new THashMap<>();

    for (int i = 0; i < 100000; ++i) {

      troveMap.put(i, Integer.toString(i));
    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        troveMap.get(j);
      }
    }

    System.out.println("100 000 000 lookups in THashMap took " + (System.currentTimeMillis() - started));


    final HashMap<Integer, String> tested = new HashMap<>();

    for (int i = 0; i < 100000; ++i) {

      tested.put(i, Integer.toString(i));
    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        tested.get(j);
      }
    }

    System.out.println("100 000 000 lookups in HashMap took " + (System.currentTimeMillis() - started));
  }


  //@Test

  public void benchmarkGetMissingKeys() {


    long started;


    final Map<Integer, String> map = new java.util.HashMap<>();

    for (int i = 0; i < 100000; ++i) {

      map.put(i, Integer.toString(i));
    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        map.get(j + 1000000);
      }
    }

    System.out.println("100 000 000 lookups in java.util.HashMap took " + (System.currentTimeMillis() - started));


    final Map<Integer, String> troveMap = new THashMap<>();

    for (int i = 0; i < 100000; ++i) {

      troveMap.put(i, Integer.toString(i));
    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        troveMap.get(j + 1000000);
      }
    }

    System.out.println("100 000 000 lookups in THashMap took " + (System.currentTimeMillis() - started));


    final HashMap<Integer, String> tested = new HashMap<>();

    for (int i = 0; i < 100000; ++i) {

      tested.put(i, Integer.toString(i));
    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        tested.get(j + 1000000);
      }
    }

    System.out.println("100 000 000 lookups in HashMap took " + (System.currentTimeMillis() - started));
  }
}

