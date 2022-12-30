// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import junit.framework.TestCase;
import one.util.streamex.IntStreamEx;
import org.junit.Assert;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

public class ContainerUtilTest extends TestCase {
  private static final Logger LOG = Logger.getInstance(ContainerUtilTest.class);
  public void testFindInstanceWorks() {
    Iterator<Object> iterator = Arrays.<Object>asList(1, new ArrayList<>(), "1").iterator();
    String string = ContainerUtil.findInstance(iterator, String.class);
    assertEquals("1", string);
  }

  public void testConcatTwoListsMustSupportListContracts() {
    Iterable<Object> concat = ContainerUtil.concat(Collections.emptySet(), Collections.emptySet());
    assertFalse(concat.iterator().hasNext());
    Iterable<Object> foo = ContainerUtil.concat(Collections.emptySet(), Collections.singletonList("foo"));
    Iterator<Object> iterator = foo.iterator();
    assertTrue(iterator.hasNext());
    assertEquals("foo", iterator.next());
    assertFalse(iterator.hasNext());
    foo = ContainerUtil.concat(Collections.singletonList("foo"), Collections.emptySet());
    iterator = foo.iterator();
    assertTrue(iterator.hasNext());
    assertEquals("foo", iterator.next());
    assertFalse(iterator.hasNext());
    foo = ContainerUtil.concat(Collections.singletonList("foo"), Collections.singleton("bar"));
    iterator = foo.iterator();
    assertTrue(iterator.hasNext());
    assertEquals("foo", iterator.next());
    assertTrue(iterator.hasNext());
    assertEquals("bar", iterator.next());
    assertFalse(iterator.hasNext());
  }

  public void testConcatMultipleListsWorks() {
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
    catch (IndexOutOfBoundsException ignore) {
    }

    try {
      l.get(4);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) {
    }
  }

  public void testConcatedListsAfterModificationMustThrowCME() {
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
    catch (ConcurrentModificationException ignore) {
    }
  }

  public void testIterateWithCondition() {
    Condition<Integer> cond = integer -> integer > 2;

    assertIterating(Arrays.asList(1, 4, 2, 5), cond, 4, 5);
    assertIterating(Arrays.asList(1, 2), cond);
    assertIterating(Collections.emptyList(), cond);
    assertIterating(Collections.singletonList(4), cond, 4);
  }

  private static void assertIterating(List<Integer> collection, Condition<? super Integer> condition, Integer... expected) {
    List<Integer> actual = ContainerUtil.newArrayList(ContainerUtil.iterate(collection, condition));
    assertEquals(Arrays.asList(expected), actual);
  }

  public void testIteratingBackward() {
    List<String> ss = new ArrayList<>();
    ss.add("a");
    ss.add("b");
    ss.add("c");

    StringBuilder log = new StringBuilder();
    for (String s : ss) {
      log.append(s);
    }

    for (String s : ContainerUtil.iterateBackward(ss)) {
      log.append(s);
    }

    assertEquals("abccba", log.toString());
  }

  public void testLockFreeSingleThreadPerformance() {
    final List<Object> stock = new CopyOnWriteArrayList<>();
    measure(stock);
    final List<Object> my = new LockFreeCopyOnWriteArrayList<>();
    measure(my);
    measure(stock);
    measure(my); // warm up
    for (int i = 0; i < 5; i++) {
      long stockElapsed = measure(stock);
      long myElapsed = measure(my);

      LOG.debug("LockFree my: " + myElapsed + "; stock: " + stockElapsed);
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

  public void testLockFreeCOWDoesNotCreateEmptyArrays() {
    LockFreeCopyOnWriteArrayList<Object> my = (LockFreeCopyOnWriteArrayList<Object>)ContainerUtil.createLockFreeCopyOnWriteList();

    for (int i = 0; i < 2; i++) {
      Object[] array = my.getArray();
      assertSame(ArrayUtilRt.EMPTY_OBJECT_ARRAY, array);
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
    List<Object> list = ContainerUtil.createLockFreeCopyOnWriteList();
    int count = 15000;
    List<Integer> ints = IntStreamEx.range(0, count).boxed().toList();
    PlatformTestUtil.startPerformanceTest("COWList add", 4500, () -> {
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

  private static void assertReallyEmpty(List<?> my) {
    assertEquals(0, my.size());

    Object[] objects = my.toArray();
    assertSame(ArrayUtilRt.EMPTY_OBJECT_ARRAY, objects);

    Iterator<?> iterator = my.iterator();
    assertSame(Collections.emptyIterator(), iterator);
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
      catch (NoSuchElementException ignore) {
      }
    }
    int size = my.size();
    Iterator<String> iterator = my.iterator();
    for (int i = 0; i < size; i++) {
      assertTrue(iterator.hasNext());
      String next = iterator.next();
      assertEquals(next, String.valueOf(i));
      iterator.remove();
      assertEquals(my.size(), size - i - 1);
      if (i == size - 1) {
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
    catch (NoSuchElementException ignore) {
    }
  }

  public void testLockFreeListStreamMustNotCMEOnParallelModifications() throws Exception {
    List<String> list = ContainerUtil.createLockFreeCopyOnWriteList();
    Future<?> future = AppExecutorUtil.getAppExecutorService().submit(
      () -> {
        for (int i = 0; i < 100_000_000; i++) {
          list.add("");
          list.remove("");
        }
      });
    for (int i = 0; i < 100_000_000; i++) {
      assertNotNull(list.stream().findFirst());
    }
    future.get();
    assertReallyEmpty(list);
  }

  public void testImmutableListEquals() {
    String value = "stringValue";
    List<String> expected = ContainerUtil.immutableList(value);
    List<String> actual = ContainerUtil.newArrayList(value);
    assertEquals(expected, actual);
  }

  public void testMergeSortedLists() {
    List<Segment> target = new ArrayList<>(Arrays.asList(
      range(0, 0),
      range(2, 2),
      range(4, 4),
      range(6, 6)
    ));
    List<Segment> source = Arrays.asList(
      range(1, 1),
      range(2, 2),
      range(2, 3)
    );
    target = mergeSegmentLists(target, source);
    assertEquals(Arrays.asList(
      range(0, 0),
      range(1, 1),
      range(2, 2),
      range(2, 3),
      range(4, 4),
      range(6, 6)
    ), target);
    target = mergeSegmentLists(target, source);
    assertEquals(Arrays.asList(
      range(0, 0),
      range(1, 1),
      range(2, 2),
      range(2, 3),
      range(4, 4),
      range(6, 6)
    ), target);
    target = mergeSegmentLists(target, Arrays.asList(
      range(-1, -1),
      range(-1, -2),
      range(-2, -3)
    ));
    assertEquals(Arrays.asList(
      range(-1, -1),
      range(-1, -2),
      range(-2, -3),
      range(0, 0),
      range(1, 1),
      range(2, 2),
      range(2, 3),
      range(4, 4),
      range(6, 6)
    ), target);
  }

  private static Segment range(int start, int end) {
    return new UnfairTextRange(start, end);
  }

  private static List<Segment> mergeSegmentLists(List<? extends Segment> list1, List<? extends Segment> list2) {
    return ContainerUtil.mergeSortedLists(list1, list2, Segment.BY_START_OFFSET_THEN_END_OFFSET, true);
  }

  public void testMergeSortedArrays() {
    List<Integer> list1 = Collections.singletonList(0);
    List<Integer> list2 = Collections.singletonList(4);
    List<Integer> m = ContainerUtil.mergeSortedLists(list1, list2, Comparator.naturalOrder(), true);
    assertEquals(Arrays.asList(0, 4), m);
    m = ContainerUtil.mergeSortedLists(list2, list1, Comparator.naturalOrder(), true);
    assertEquals(Arrays.asList(0, 4), m);
  }

  public void testMergeSortedArrays2() {
    int[] a1 = {0, 4};
    int[] a2 = {4};
    int[] m = ArrayUtil.mergeSortedArrays(a1, a2, true);
    Assert.assertArrayEquals(new int[]{0, 4}, m);
    m = ArrayUtil.mergeSortedArrays(a2, a1, true);
    Assert.assertArrayEquals(new int[]{0, 4}, m);
  }

  public void testImmutableListSubList() {
    List<Integer> list = ContainerUtil.immutableList(0, 1, 2, 3, 4);
    List<Integer> subList = list.subList(1, 4);
    UsefulTestCase.assertOrderedEquals(subList, 1, 2, 3);
    List<Integer> subSubList = subList.subList(1, 2);
    UsefulTestCase.assertOrderedEquals(subSubList, 2);
    assertEquals(new ArrayList<>(subSubList), subSubList);
  }
  public void testFlatMap() {
    List<Integer> list = ContainerUtil.flatMap(List.of(0, 1), i->List.of(i,i));
    assertEquals(List.of(0,0,1,1), list);
  }
}