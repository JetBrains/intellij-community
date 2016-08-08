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

package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import junit.framework.TestCase;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContainerUtilTest extends TestCase {
  public void testFindInstanceOf() {
    Iterator<Object> iterator = Arrays.<Object>asList(new Integer(1), new ArrayList(), "1").iterator();
    String string = (String)ContainerUtil.find(iterator, FilteringIterator.instanceOf(String.class));
    assertEquals("1", string);
  }

  public void testConcatMulti() {
    List<Integer> l = ContainerUtil.concat(Arrays.asList(1, 2), Collections.EMPTY_LIST, Arrays.asList(3, 4));
    assertEquals(4, l.size());
    assertEquals(1, (int)l.get(0));
    assertEquals(2, (int)l.get(1));
    assertEquals(3, (int)l.get(2));
    assertEquals(4, (int)l.get(3));

    try {
      l.get(-1);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) {
    }

    try {
      l.get(4);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) {
    }
  }

  public void testIterateWithCondition() throws Exception {
    Condition<Integer> cond = integer -> integer > 2;

    asserIterating(Arrays.asList(1, 4, 2, 5), cond, 4, 5);
    asserIterating(Arrays.asList(1, 2), cond);
    asserIterating(Collections.<Integer>emptyList(), cond);
    asserIterating(Arrays.asList(4), cond, 4);
  }

  private static void asserIterating(List<Integer> collection, Condition<Integer> condition, Integer... expected) {
    Iterable<Integer> it = ContainerUtil.iterate(collection, condition);
    List<Integer> actual = new ArrayList<>();
    for (Integer each : it) {
      actual.add(each);
    }
    assertEquals(Arrays.asList(expected), actual);
  }

  public void testIteratingBackward() throws Exception {
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

    assertEquals("abccba", log);
  }

  public void testMergeSortedArrays() {
    TIntArrayList x1 = new TIntArrayList(new int[]{0, 2, 4, 6});
    TIntArrayList y1 = new TIntArrayList(new int[]{0, 2, 4, 6});
    TIntArrayList x2 = new TIntArrayList(new int[]{1, 2, 2});
    TIntArrayList y2 = new TIntArrayList(new int[]{1, 2, 3});
    ContainerUtil.mergeSortedArrays(x1, y1, x2, y2);
    assertEquals(new TIntArrayList(new int[]{0, 1, 2, 2, 4, 6}), x1);
    assertEquals(new TIntArrayList(new int[]{0, 1, 2, 3, 4, 6}), y1);
    x2 = new TIntArrayList(new int[]{1, 2, 2});
    y2 = new TIntArrayList(new int[]{1, 2, 3});
    ContainerUtil.mergeSortedArrays(x1, y1, x2, y2);
    assertEquals(new TIntArrayList(new int[]{0, 1, 2, 2, 4, 6}), x1);
    assertEquals(new TIntArrayList(new int[]{0, 1, 2, 3, 4, 6}), y1);

    x2 = new TIntArrayList(new int[]{-1, -1, -2});
    y2 = new TIntArrayList(new int[]{-1, -2, -3});
    ContainerUtil.mergeSortedArrays(x1, y1, x2, y2);
    assertEquals(new TIntArrayList(new int[]{-1, -1, -2, 0, 1, 2, 2, 4, 6}), x1);
    assertEquals(new TIntArrayList(new int[]{-1, -2, -3, 0, 1, 2, 3, 4, 6}), y1);
  }

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
      assertTrue("lockFree: "+myElapsed+"; stock: "+stockElapsed, (myElapsed - stockElapsed+0.0)/myElapsed < 0.1);
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

  //public void testLockFreeContendedPerformance() throws Exception {
  //  final List<Object> lockFree = new LockFreeCopyOnWriteArrayList<Object>();
  //  final List<Object> stock = new CopyOnWriteArrayList<Object>();
  //  LockFreeCopyOnWriteArrayListExpBackoff2<Object> back = new LockFreeCopyOnWriteArrayListExpBackoff2<Object>();
  //
  //  measureContended(stock);
  //  measureContended(lockFree);
  //  measureContended(back);
  //  for (int i=0; i<10; i++) {
  //    long stockElapsed = measureContended(stock);
  //    LockFreeCopyOnWriteArrayListExpBackoff2.RETRIES.set(0);
  //    LockFreeCopyOnWriteArrayList.RETRIES.set(0);
  //    long myElapsed = measureContended(lockFree);
  //    long bElapsed = measureContended(back);
  //    int bRetries = LockFreeCopyOnWriteArrayListExpBackoff2.RETRIES.get();
  //    int mRetries = LockFreeCopyOnWriteArrayList.RETRIES.get();
  //
  //    System.out.println("Contended: stock: "+stockElapsed
  //                       +"; lockFree: "+myElapsed+"; retries per op: "+(mRetries/1000000.0/2)
  //                       +"; backoff: "+bElapsed+"; retries per op: "+(bRetries/1000000.0/2));
  //    //assertTrue("my: "+myElapsed+"; stock: "+stockElapsed, Math.abs(myElapsed - stockElapsed+0.0)/myElapsed < 0.1);
  //  }
  //}
  //private long measureContended(final List<Object> list) throws Exception {
  //  long start = System.currentTimeMillis();
  //  int N = /*2;//*/Runtime.getRuntime().availableProcessors();
  //  Thread[] threads = new Thread[N];
  //  final AtomicReference<Exception> ex = new AtomicReference<Exception>();
  //  for (int i=0; i< N; i++) {
  //    Thread thread = new Thread(new Runnable() {
  //      @Override
  //      public void run() {
  //        for (int n = 0; n < 1000000; n++) {
  //          list.add(this);
  //          boolean removed = list.remove(this);
  //          if (!removed) {
  //            ex.set(new Exception());
  //          }
  //        }
  //      }
  //    },"t" + i);
  //    threads[i] = thread;
  //    thread.start();
  //  }
  //  for (int i=0; i< N; i++) {
  //    try {
  //      threads[i].join();
  //    }
  //    catch (InterruptedException e) {
  //      throw new RuntimeException(e);
  //    }
  //  }
  //  if (ex.get() != null) throw ex.get();
  //  long finish = System.currentTimeMillis();
  //  assertTrue(list.isEmpty());
  //  return finish - start;
  //}

  public void testLockFreeCOWDoesNotCreateEmptyArrays() {
    LockFreeCopyOnWriteArrayList<Object> my = (LockFreeCopyOnWriteArrayList<Object>)ContainerUtil.createLockFreeCopyOnWriteList();
    //LockFreeCopyOnWriteArrayListExpBackoff2<Object> my = new LockFreeCopyOnWriteArrayListExpBackoff2<Object>();

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

  public void testCOWListPerformanceAdd() {
    List<Object> my = ContainerUtil.createLockFreeCopyOnWriteList();
    PlatformTestUtil.startPerformanceTest("COWList add", 3000, () -> {
      List<Object> local = my;
      for (int it=0; it<10; it++) {
        local.clear();
        for (int i = 0; i < 15000; i++) {
          local.add(i);
        }
      }
    }).useLegacyScaling().assertTiming();
    for (int i = 0; i < my.size(); i++) {
      assertEquals(i, my.get(i));
    }
  }

  private static void assertReallyEmpty(List<Object> my) {
    assertEquals(0, my.size());

    Object[] objects = my.toArray();
    assertSame(ArrayUtil.EMPTY_OBJECT_ARRAY, objects);

    Iterator<Object> iterator = my.iterator();
    assertSame(EmptyIterator.getInstance(), iterator);
  }

  public void testIdenticalItemsInLockFreeCOW() {
    List<String> list = ContainerUtil.createLockFreeCopyOnWriteList(Arrays.asList("a", "b"));
    list.add("a");
    assertEquals(3, list.size());
    list.remove("a");
    assertEquals(2, list.size());
    list.remove("a");
    assertEquals(1, list.size());
  }

  public void testLockFreeCOWIteratorRemove() {
    List<String> seq = Arrays.asList("0", "1", "2", "3", "4");
    LockFreeCopyOnWriteArrayList<String> my = (LockFreeCopyOnWriteArrayList<String>)ContainerUtil.createLockFreeCopyOnWriteList(seq);
    {
      Iterator<String> iterator = my.iterator();
      try {
        iterator.remove();
        fail("must not be able to remove before next() call");
      }
      catch (NoSuchElementException ignored) {
      }
    }
    int size = my.size();
    Iterator<String> iterator = my.iterator();
    for (int i = 0; i<size; i++) {
      assertTrue(iterator.hasNext());
      String next = iterator.next();
      assertEquals(next, String.valueOf(i));
      iterator.remove();
      assertEquals(my.size(), size - i-1);
      if (i == size-1) {
        assertTrue(my.isEmpty());
      }
      else {
        assertEquals(my.toArray()[0], String.valueOf(i+1));
        assertEquals(my.toString(), seq.subList(i+1,seq.size()).toString());
      }
    }

    try {
      iterator.remove();
      fail("must not be able to double remove()");
    }
    catch (NoSuchElementException ignored) {
    }
  }

  public void testImmutableListEquals() {
    String value = "stringValue";
    List<String> expected = ContainerUtil.immutableList(value);
    List<String> actual = ContainerUtil.newArrayList(value);
    assertEquals(expected, actual);
  }

}
