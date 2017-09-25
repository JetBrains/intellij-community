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

package com.intellij.util.containers;

import com.intellij.util.Assertion;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class SequenceIteratorTest extends TestCase {
  private final Assertion CHECK = new Assertion();

  public void testOneIterator() {
    Iterator<Object> iterator = iterate("1", "2");
    Iterator<Object> seq = ContainerUtil.concatIterators(iterator);
    CHECK.compareAll(new Object[]{"1", "2"}, ContainerUtil.collect(seq));
  }

  public void testTwoNotEmpties() {
    Iterator<Object> seq = ContainerUtil.concatIterators(iterate("1", "2"), iterate("3", "4"));
    CHECK.compareAll(new Object[]{"1", "2", "3", "4"}, ContainerUtil.collect(seq));
  }

  public void testAllEmpty() {
    Assert.assertFalse(ContainerUtil.concatIterators(empty()).hasNext());
    Assert.assertFalse(ContainerUtil.concatIterators(empty(), empty()).hasNext());
  }

  public void testIntermediateEmpty() {
    Iterator<Object> seq = ContainerUtil.concatIterators(iterate("1", "2"), empty(), iterate("3", "4"));
    CHECK.compareAll(new Object[]{"1", "2", "3", "4"}, ContainerUtil.collect(seq));
  }

  public void testFirstEmpty() {
    Iterator<Object> seq = ContainerUtil.concatIterators(empty(), iterate("1", "2"));
    CHECK.compareAll(new Object[]{"1", "2"}, ContainerUtil.collect(seq));
  }

  private static Iterator<Object> iterate(String first, String second) {
    return Arrays.asList(new Object[]{first, second}).iterator();
  }

  private static Iterator empty() {
    return new ArrayList().iterator();
  }


  public void testSimple() {
    final Iterator<Integer> iterator = compose(Arrays.asList(iter(arr1), iter(arr2), iter(arr3)));
    int cnt = 0;
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      ++ cnt;
    }
    Assert.assertEquals(arr1.length + arr2.length + arr3.length, cnt);
  }

  private static Iterator<Integer> compose(List<Iterator<Integer>> iterators) {
    return ContainerUtil.concatIterators(iterators);
  }

  public void testOne() {
    final Iterator<Integer> iterator = compose(Arrays.asList(iter(arr1)));
    int cnt = 0;
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      ++ cnt;
    }
    Assert.assertEquals(arr1.length, cnt);
  }

  public void testOneOne() {
    final Iterator<Integer> iterator = compose(Arrays.asList(iter(new Integer[]{1})));
    int cnt = 0;
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      ++ cnt;
    }
    Assert.assertEquals(1, cnt);
  }

  public void testEmpty() {
    final Iterator<Integer> iterator = compose(Arrays.asList(iter(new Integer[]{})));
    int cnt = 0;
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      ++ cnt;
    }
    Assert.assertEquals(0, cnt);
  }

  public void testManyEmpty() {
    final Iterator<Integer> iterator =
      compose(Arrays.asList(iter(new Integer[]{}), iter(new Integer[]{}), iter(new Integer[]{})));
    int cnt = 0;
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      ++ cnt;
    }
    Assert.assertEquals(0, cnt);
  }

  public void testRemoveSimple() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(arr1));
    final ArrayList<Integer> list2 = new ArrayList<>(Arrays.asList(arr2));
    final ArrayList<Integer> list3 = new ArrayList<>(Arrays.asList(arr3));

    final Iterator<Integer> iterator =
      compose(Arrays.asList(list1.iterator(), list2.iterator(), list3.iterator()));
    int cnt = 0;
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      if ((cnt - 2) % 5 == 0) {
        iterator.remove();
      }
      ++ cnt;
    }
    Assert.assertTrue(! list1.contains(3));
    Assert.assertTrue(! list2.contains(13));
    Assert.assertTrue(! list3.contains(103));
  }

  public void testRemoveAfterLast() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(arr1));
    final Iterator<Integer> it1 = list1.iterator();
    while (it1.hasNext()) {
      Integer next = it1.next();
    }
    it1.remove(); // ok, removes last
    Assert.assertTrue(! list1.contains(5));
    list1.add(5);

    final ArrayList<Integer> list2 = new ArrayList<>(Arrays.asList(arr2));
    final ArrayList<Integer> list3 = new ArrayList<>(Arrays.asList(arr3));

    final Iterator<Integer> iterator =
      compose(Arrays.asList(list1.iterator(), list2.iterator(), list3.iterator()));
    while (iterator.hasNext()) {
      Integer next = iterator.next();
    }
    iterator.remove();
    Assert.assertTrue(! list3.contains(105));
  }

  public void testRemoveOnlyOne() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(new Integer[]{1}));
    final Iterator<Integer> iterator = compose(Arrays.asList(list1.iterator()));
    iterator.next();
    iterator.remove();
    Assert.assertTrue(list1.isEmpty());
  }

  public void testIterateWithEmptyInside() {
    final Iterator<Integer> iterator = compose(Arrays.asList(iter(arr1), iter(new Integer[]{}), iter(arr3)));
    int cnt = 0;
    int sum = 0;
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      ++ cnt;
      sum += next;
    }
    Assert.assertEquals(arr1.length + arr3.length, cnt);
    Assert.assertEquals(530, sum);
  }

  public void testRemoveIfNextNotCalled() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(new Integer[]{1}));
    final Iterator<Integer> iterator = compose(Arrays.asList(list1.iterator()));
    try {
      iterator.remove();
      Assert.assertTrue(false);
    } catch (IllegalStateException e) {
      // ok
    }
  }

  public void testRemoveTwice() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(new Integer[]{1, 2, 3, 4, 5}));
    final Iterator<Integer> iterator = compose(Arrays.asList(list1.iterator()));
    try {
      iterator.next();
      iterator.remove();
      iterator.remove();  // wrong, next() should be called inside
      Assert.assertTrue(false);
    } catch (IllegalStateException e) {
      // ok
    }
  }

  public void testRemoveAll() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(1, 2));
    final ArrayList<Integer> list2 = new ArrayList<>(Arrays.asList(3, 4));
    final ArrayList<Integer> list3 = new ArrayList<>(Arrays.asList(5, 6));

    final Iterator<Integer> iterator =
      compose(Arrays.asList(list1.iterator(), list2.iterator(), list3.iterator()));
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      iterator.remove();
    }
    Assert.assertTrue(list1.isEmpty());
    Assert.assertTrue(list2.isEmpty());
    Assert.assertTrue(list3.isEmpty());
  }

  public void testRemoveAllWithEmptyInside() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(1, 2));
    final ArrayList<Integer> list2 = new ArrayList<>();
    final ArrayList<Integer> list3 = new ArrayList<>(Arrays.asList(5, 6));

    final Iterator<Integer> iterator =
      compose(Arrays.asList(list1.iterator(), list2.iterator(), list3.iterator()));
    while (iterator.hasNext()) {
      Integer next = iterator.next();
      iterator.remove();
    }
    Assert.assertTrue(list1.isEmpty());
    Assert.assertTrue(list2.isEmpty());
    Assert.assertTrue(list3.isEmpty());
  }

  public void testRemoveLastAndFirstINNext() {
    final ArrayList<Integer> list1 = new ArrayList<>(Arrays.asList(1, 2));
    final ArrayList<Integer> list2 = new ArrayList<>(Arrays.asList(3, 4));
    final ArrayList<Integer> list3 = new ArrayList<>(Arrays.asList(5, 6));

    final Iterator<Integer> iterator =
      compose(Arrays.asList(list1.iterator(), list2.iterator(), list3.iterator()));
    iterator.next();
    iterator.next();
    iterator.remove();
    iterator.next();
    iterator.remove();

    Assert.assertTrue(list1.size() == 1 && ! list1.contains(2));
    Assert.assertTrue(list2.size() == 1 && ! list1.contains(3));
    Assert.assertTrue(list3.size() == 2);
  }

  private static final Integer[] arr1 = {1,2,3,4,5};
  private static final Integer[] arr2 = {11,12,13,14,15};
  private static final Integer[] arr3= {101,102,103,104,105};

  private Iterator<Integer> iter(final Integer[] arr) {
    return Arrays.asList(arr).iterator();
  }

}
