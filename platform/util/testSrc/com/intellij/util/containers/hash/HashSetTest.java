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


import gnu.trove.THashSet;

import org.junit.Assert;

import org.junit.Test;


import java.util.HashSet;

import java.util.Iterator;

import java.util.Set;


public class HashSetTest {


  @Test

  public void testAddContains() {

    final HashSet<Integer> tested = new HashSet<>();

    for (int i = 0; i < 1000; ++i) {

      tested.add(i);

    }

    Assert.assertEquals(1000, tested.size());

    for (int i = 0; i < 1000; ++i) {

      Assert.assertTrue(tested.contains(i));

    }

  }


  @Test

  public void testAddContainsRemove() {

    final HashSet<Integer> tested = new HashSet<>();

    for (int i = 0; i < 1000; ++i) {

      tested.add(i);

    }

    Assert.assertEquals(1000, tested.size());

    for (int i = 0; i < 1000; i += 2) {

      Assert.assertTrue(tested.remove(i));

    }

    Assert.assertEquals(500, tested.size());

    for (int i = 0; i < 1000; ++i) {

      if (i % 2 == 0) {

        Assert.assertFalse(tested.contains(i));

      }
      else {

        Assert.assertTrue(tested.contains(i));

      }

    }

  }


  @Test

  public void iterator() {

    final HashSet<Integer> tested = new HashSet<>();

    final Set<Integer> set = new java.util.HashSet<>();


    for (int i = 0; i < 10000; ++i) {

      tested.add(i);

      set.add(i);

    }

    for (Integer key : tested) {

      Assert.assertTrue(set.remove(key));

    }

    Assert.assertEquals(0, set.size());

  }


  @Test

  public void iterator2() {

    final HashSet<Integer> tested = new HashSet<>();

    final Set<Integer> set = new HashSet<>();


    for (int i = 0; i < 10000; ++i) {

      tested.add(i);

      set.add(i);

    }

    Iterator<Integer> it = tested.iterator();

    while (it.hasNext()) {

      final int i = it.next();

      if (i % 2 == 0) {

        it.remove();

        Assert.assertTrue(set.remove(i));

      }

    }


    Assert.assertEquals(5000, tested.size());


    it = tested.iterator();

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


    final Set<Integer> map = new java.util.HashSet<>();

    for (int i = 0; i < 100000; ++i) {

      map.add(i);

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        map.contains(j);

      }

    }

    System.out.println("100 000 000 lookups in java.util.HashSet took " + (System.currentTimeMillis() - started));


    final Set<Integer> troveSet = new THashSet<>();

    for (int i = 0; i < 100000; ++i) {

      troveSet.add(i);

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        troveSet.contains(j);

      }

    }

    System.out.println("100 000 000 lookups in THashSet took " + (System.currentTimeMillis() - started));


    final HashSet<Integer> tested = new HashSet<>();

    for (int i = 0; i < 100000; ++i) {

      tested.add(i);

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        tested.contains(j);

      }

    }

    System.out.println("100 000 000 lookups in HashSet took " + (System.currentTimeMillis() - started));

  }


  //@Test

  public void benchmarkGetMissingKeys() {


    long started;


    final Set<Integer> map = new java.util.HashSet<>();

    for (int i = 0; i < 100000; ++i) {

      map.add(i);

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        map.contains(j + 1000000);

      }

    }

    System.out.println("100 000 000 lookups in java.util.HashSet took " + (System.currentTimeMillis() - started));


    final Set<Integer> troveSet = new THashSet<>();

    for (int i = 0; i < 100000; ++i) {

      troveSet.add(i);

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        troveSet.contains(j + 1000000);

      }

    }

    System.out.println("100 000 000 lookups in THashSet took " + (System.currentTimeMillis() - started));


    final HashSet<Integer> tested = new HashSet<>();

    for (int i = 0; i < 100000; ++i) {

      tested.add(i);

    }

    started = System.currentTimeMillis();

    for (int i = 0; i < 1000; ++i) {

      for (int j = 0; j < 100000; ++j) {

        tested.contains(j + 1000000);

      }

    }

    System.out.println("100 000 000 lookups in HashSet took " + (System.currentTimeMillis() - started));

  }

}

