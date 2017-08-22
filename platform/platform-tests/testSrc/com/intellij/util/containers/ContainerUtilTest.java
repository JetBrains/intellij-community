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
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import one.util.streamex.IntStreamEx;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

public class ContainerUtilTest {
  @Test
  public void testFindInstanceOf() {
    Iterator<Object> iterator = Arrays.<Object>asList(new Integer(1), new ArrayList(), "1").iterator();
    String string = (String)ContainerUtil.find(iterator, FilteringIterator.instanceOf(String.class));
    assertEquals("1", string);
  }

  @Test
  public void testConcatMulti() {
    List<Integer> l = ContainerUtil.concat(Arrays.asList(1, 2), Collections.emptyList(), Arrays.asList(3, 4));
    assertEquals(4, l.size());
    assertEquals(1, (int)l.get(0));
    assertEquals(2, (int)l.get(1));
    assertEquals(3, (int)l.get(2));
    assertEquals(4, (int)l.get(3));

    try {
      l.get(-1);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) { }

    try {
      l.get(4);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) { }
  }

  @Test
  public void testConcatCME() {
    List<Integer> a1 = new ArrayList<>(Arrays.asList(0, 1));
    List<Integer> l = ContainerUtil.concat(a1, Arrays.asList(2, 3), ContainerUtil.emptyList());
    assertEquals(4, l.size());
    for (int i = 0; i < l.size(); i++) {
      int at = l.get(i);
      assertEquals(i, at);
    }

    try {
      a1.clear();
      l.get(3);
      fail();
    }
    catch (ConcurrentModificationException ignore) { }
  }

  @Test
  public void testIterateWithCondition() {
    Condition<Integer> cond = integer -> integer > 2;

    assertIterating(Arrays.asList(1, 4, 2, 5), cond, 4, 5);
    assertIterating(Arrays.asList(1, 2), cond);
    assertIterating(Collections.emptyList(), cond);
    assertIterating(Collections.singletonList(4), cond, 4);
  }

  private static void assertIterating(List<Integer> collection, Condition<Integer> condition, Integer... expected) {
    List<Integer> actual = ContainerUtil.newArrayList(ContainerUtil.iterate(collection, condition));
    assertEquals(Arrays.asList(expected), actual);
  }

  @Test
  public void testIteratingBackward() {
    List<String> ss = new ArrayList<>();
    ss.add("a");
    ss.add("b");
    ss.add("c");

    String log = "";
    for (String s : ss) {
      log += s;
    }

    for (String s : ContainerUtil.iterateBackward(ss)) {
      log += s;
    }

    assertEquals("abc" + "cba", log);
  }

  @Test
  public void testLockFreeSingleThreadPerformance() {
    final List<Object> my = new LockFreeCopyOnWriteArrayList<>();
    final List<Object> stock = new CopyOnWriteArrayList<>();

    measure(stock);
    measure(my);
    measure(stock);
    measure(my); // warm up
    for (int i=0; i<10; i++) {
      long stockElapsed = measure(stock);
      long myElapsed = measure(my);

      System.out.println("LockFree my: "+myElapsed+"; stock: "+stockElapsed);
      assertTrue("lockFree: " + myElapsed + "; stock: " + stockElapsed, (myElapsed - stockElapsed + 0.0) / myElapsed < 0.1);
    }
  }

  private long measure(List<Object> list) {
    long start = System.currentTimeMillis();
    for (int n = 0; n < 10000000; n++) {
      list.add(this);
      list.remove(this);
      list.add(this);
      list.remove(0);
    }
    long finish = System.currentTimeMillis();
    assertTrue(list.isEmpty());
    return finish - start;
  }

  @Test
  public void testLockFreeCOWDoesNotCreateEmptyArrays() {
    LockFreeCopyOnWriteArrayList<Object> my = (LockFreeCopyOnWriteArrayList<Object>)ContainerUtil.createLockFreeCopyOnWriteList();

    for (int i = 0; i < 2; i++) {
      Object[] array = my.getArray();
      assertSame(ArrayUtil.EMPTY_OBJECT_ARRAY, array);
      assertReallyEmpty(my);
      my.add(this);
      my.remove(this);
      assertReallyEmpty(my);
      my.add(this);
      my.remove(0);
      assertReallyEmpty(my);
      my.add(this);
      my.clear();
      assertReallyEmpty(my);
    }
  }

  @Test
  public void testCOWListPerformanceAdd() {
    List<Object> list = ContainerUtil.createLockFreeCopyOnWriteList();
    int count = 15000;
    List<Integer> ints = IntStreamEx.range(0, count).boxed().toList();
    PlatformTestUtil.startPerformanceTest("COWList add", 3500, () -> {
      for (int it = 0; it < 10; it++) {
        list.clear();
        for (int i = 0; i < count; i++) {
          list.add(ints.get(i));
        }
      }
    }).assertTiming();
    for (int i = 0; i < list.size(); i++) {
      assertEquals(i, list.get(i));
    }
  }

  private static void assertReallyEmpty(List<Object> my) {
    assertEquals(0, my.size());

    Object[] objects = my.toArray();
    assertSame(ArrayUtil.EMPTY_OBJECT_ARRAY, objects);

    Iterator<Object> iterator = my.iterator();
    assertSame(EmptyIterator.getInstance(), iterator);
  }

  @Test
  public void testIdenticalItemsInLockFreeCOW() {
    List<String> list = ContainerUtil.createLockFreeCopyOnWriteList(Arrays.asList("a", "b"));
    list.add("a");
    assertEquals(3, list.size());
    list.remove("a");
    assertEquals(2, list.size());
    list.remove("a");
    assertEquals(1, list.size());
  }

  @Test
  public void testLockFreeCOWIteratorRemove() {
    List<String> seq = Arrays.asList("0", "1", "2", "3", "4");
    LockFreeCopyOnWriteArrayList<String> my = (LockFreeCopyOnWriteArrayList<String>)ContainerUtil.createLockFreeCopyOnWriteList(seq);
    {
      Iterator<String> iterator = my.iterator();
      try {
        iterator.remove();
        fail("must not be able to remove before next() call");
      }
      catch (NoSuchElementException ignore) { }
    }
    int size = my.size();
    Iterator<String> iterator = my.iterator();
    for (int i = 0; i<size; i++) {
      assertTrue(iterator.hasNext());
      String next = iterator.next();
      assertEquals(next, String.valueOf(i));
      iterator.remove();
      assertEquals(my.size(), size - i - 1);
      if (i == size-1) {
        assertTrue(my.isEmpty());
      }
      else {
        assertEquals(my.toArray()[0], String.valueOf(i + 1));
        assertEquals(my.toString(), seq.subList(i + 1, seq.size()).toString());
      }
    }

    try {
      iterator.remove();
      fail("must not be able to double remove()");
    }
    catch (NoSuchElementException ignore) { }
  }

  @Test
  public void testImmutableListEquals() {
    String value = "stringValue";
    List<String> expected = ContainerUtil.immutableList(value);
    List<String> actual = ContainerUtil.newArrayList(value);
    assertEquals(expected, actual);
  }
}