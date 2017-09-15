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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.PairFunction;
import com.intellij.util.Processor;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.util.Conditions.not;
import static com.intellij.util.containers.JBIterable.Split.*;

/**
 * @author gregsh
 *
 * @noinspection ArraysAsListWithZeroOrOneArgument
 */
public class TreeTraverserTest extends TestCase {


  /**
   * <pre>
   *                   --- 5
   *           ---  2  --- 6
   *         /         --- 7
   *       /
   *     /          --- 8
   *   1   ---  3   --- 9
   *     \          --- 10
   *      \
   *       \           --- 11
   *         ---  4    --- 12
   *                   --- 13
   * </pre>
   */
  private static Map<Integer, Collection<Integer>> numbers() {
    return ContainerUtil.<Integer, Collection<Integer>>immutableMapBuilder().
      put(1, Arrays.asList(2, 3, 4)).
      put(2, Arrays.asList(5, 6, 7)).
      put(3, Arrays.asList(8, 9, 10)).
      put(4, Arrays.asList(11, 12, 13)).
      build();
  }

  private static Map<Integer, Collection<Integer>> numbers2() {
    return ContainerUtil.<Integer, Collection<Integer>>immutableMapBuilder().
      put(1, Arrays.asList(2, 3, 4)).
      put(2, Arrays.asList(5, 6, 7)).
      put(3, Arrays.asList(8, 9, 10)).
      put(4, Arrays.asList(11, 12, 13)).
      put(5, Arrays.asList(14, 15, 16)).
      put(6, Arrays.asList(17, 18, 19)).
      put(7, Arrays.asList(20, 21, 22)).
      put(8, Arrays.asList(23, 24, 25)).
      put(9, Arrays.asList(26, 27, 28)).
      put(10, Arrays.asList(29, 30, 31)).
      put(11, Arrays.asList(32, 33, 34)).
      put(12, Arrays.asList(35, 36, 37)).
      build();
  }

  private static final Condition<Integer> IS_ODD = integer -> integer.intValue() % 2 == 1;

  private static final Condition<Integer> IS_POSITIVE = integer -> integer.intValue() > 0;

  private static Condition<Integer> inRange(int s, int e) {
    return integer -> s <= integer && integer <= e;
  }

  public static final Function<Integer, List<Integer>> WRAP_TO_LIST = integer -> ContainerUtil.newArrayList(integer);

  public static final Function<Integer, Integer> DIV_2 = k -> k / 2;

  private static final Function<Integer, Integer> INCREMENT = k -> k + 1;

  private static final Function<Integer, Integer> SQUARE = k -> k * k;

  private static final PairFunction<Integer, Integer, Integer> FIBONACCI = (k1, k2) -> k2 + k1;

  private static final Function<Integer, Integer> FIBONACCI2 = new JBIterable.StatefulTransform<Integer, Integer>() {
    int k0;
    @Override
    public Integer fun(Integer k) {
      int t = k0;
      k0 = k;
      return t + k;
    }
  };

  @NotNull
  private static Condition<Integer> LESS_THAN(final int max) {
    return integer -> integer < max;
  }

  @NotNull
  private static Condition<Integer> LESS_THAN_MOD(final int max) {
    return integer -> integer % max < max / 2;
  }

  @NotNull
  private static <E> JBIterable.StatefulFilter<E> UP_TO(final E o) {
    return new JBIterable.StatefulFilter<E>() {
      boolean b;

      @Override
      public boolean value(E e) {
        if (b) return false;
        b = Comparing.equal(e, o);
        return true;
      }
    };
  }


  // JBIterator ----------------------------------------------

  public void testIteratorContracts() {
    Processor<Runnable> tryCatch = (r) -> {
      try {
        r.run();
        return true;
      }
      catch (NoSuchElementException e) {
        return false;
      }
    };
    JBIterator<Integer> it = JBIterator.from(Arrays.asList(1, 2, 3, 4).iterator());
    assertFalse(tryCatch.process(it::current));
    assertTrue(it.hasNext());
    assertFalse(tryCatch.process(it::current));
    assertTrue(it.advance());                  // advance->1
    assertEquals(new Integer(1), it.current());
    assertTrue(it.hasNext());
    assertTrue(it.hasNext());
    assertEquals(new Integer(2), it.next());   // advance->2
    assertEquals(new Integer(2), it.current());
    assertEquals(new Integer(2), it.current());
    assertTrue(it.advance());                  // advance->3
    assertEquals(new Integer(4), it.next());   // advance->4
    assertFalse(it.hasNext());
    assertFalse(it.hasNext());
    assertFalse(it.advance());
    assertFalse(tryCatch.process(it::current));
    assertFalse(tryCatch.process(it::next));
    assertFalse(it.hasNext());
  }

  public void testIteratorContractsCurrent() {
    JBIterator<Integer> it = JBIterator.from(JBIterable.of(1).iterator());
    assertTrue(it.advance());
    assertEquals(new Integer(1), it.current());
    assertFalse(it.hasNext());
    assertEquals(new Integer(1), it.current());
  }

  public void testCursorIterableContract() {
    List<Integer> list = ContainerUtil.newArrayList();
    JBIterable<Integer> orig = JBIterable.generate(1, INCREMENT).take(5);
    for (JBIterator<Integer> it : JBIterator.cursor(JBIterator.from(orig.iterator()))) {
      it.current();
      it.hasNext();
      list.add(it.current());
    }
    assertEquals(orig.toList(), list);
  }

  public void testCursorIteratorContract() {
    JBIterable<Integer> orig = JBIterable.generate(1, INCREMENT).take(5);
    JBIterator<JBIterator<Integer>> it = JBIterator.from(JBIterator.cursor(
      JBIterator.from(orig.iterator())).iterator());
    List<Integer> list = ContainerUtil.newArrayList();
    while (it.advance()) {
      it.hasNext();
      list.add(it.current().current());
    }
    assertEquals(orig.toList(), list);
  }

  public void testCursorTransform() {
    JBIterable<Integer> orig = JBIterable.generate(1, INCREMENT).take(5);

    List<Integer> expected = ContainerUtil.newArrayList(1, 2, 3, 4, 5);
    List<Integer> expectedOdd = ContainerUtil.newArrayList(1, 3, 5);
    assertEquals(expected, JBIterator.cursor(JBIterator.from(orig.iterator())).transform(o -> o.current()).toList());
    assertEquals(expected.size(), JBIterator.cursor(JBIterator.from(orig.iterator())).last().current().intValue());
    assertEquals(expectedOdd, JBIterator.cursor(JBIterator.from(orig.iterator())).transform(o -> o.current()).filter(IS_ODD).toList());
    assertEquals(expectedOdd, JBIterator.cursor(JBIterator.from(orig.iterator())).filter(o -> IS_ODD.value(o.current())).transform(o -> o.current()).toList());
    assertEquals(expected.subList(0, 4), JBIterator.cursor(JBIterator.from(orig.iterator())).filter(o -> o.hasNext()).transform(o -> o.current()).toList());
  }

  public void testIteratorContractsSkipAndStop() {
    final AtomicInteger count = new AtomicInteger(0);
    JBIterator<Integer> it = new JBIterator<Integer>() {

      @Override
      protected Integer nextImpl() {
        return count.get() < 0 ? stop() :
               count.incrementAndGet() < 10 ? skip() :
               (Integer)count.addAndGet(-count.get() - 1);
      }
    };
    assertEquals(JBIterable.of(-1).toList(), JBIterable.once(it).toList());
  }

  // JBIterable ----------------------------------------------

  public void testFirstLastSingle() {
    assertEquals(null, JBIterable.empty().first());
    assertEquals(null, JBIterable.empty().last());
    assertEquals(null, JBIterable.empty().single());

    assertEquals("a", JBIterable.generate("a", o -> o + "a").first());
    assertEquals("aaa", JBIterable.generate("a", o -> o + "a").take(3).last());
    assertEquals("a", JBIterable.generate("a", o -> o + "a").take(1).single());
    assertEquals(null, JBIterable.generate("a", o -> o + "a").take(2).single());

    assertEquals("a", JBIterable.from(Arrays.asList("a", "aa", "aaa")).first());
    assertEquals("aaa", JBIterable.from(Arrays.asList("a", "aa", "aaa")).last());
    assertEquals("a", JBIterable.of("a").single());
    assertEquals(null, JBIterable.of("a", "aa", "aaa").single());
  }

  public void testOfAppendNulls() {
    Integer o = null;
    JBIterable<Integer> it = JBIterable.of(o).append(o).append(JBIterable.empty());
    assertTrue(it.isEmpty());
    assertSame(it, JBIterable.empty());
  }

  public void testAppend() {
    JBIterable<Integer> it = JBIterable.of(1, 2, 3).append(JBIterable.of(4, 5, 6)).append(JBIterable.empty()).append(7);
    assertEquals(7, it.size());
    assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), it.toList());
    assertTrue(it.contains(5));
  }

  public void testGenerateRepeat() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(3).repeat(3);
    assertEquals(9, it.size());
    assertEquals(Arrays.asList(1, 2, 3, 1, 2, 3, 1, 2, 3), it.toList());
  }

  public void testSkipTakeSize() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).skip(10).take(10);
    assertEquals(10, it.size());
    assertEquals(new Integer(11), it.first());
  }

  public void testFlattenSkipTake() {
    assertEquals(1, JBIterable.of(1).flatMap(o -> JBIterable.of(o)).take(1).take(1).take(1).size());
    assertEquals((Integer)1, JBIterable.of(1).flatMap(o -> JBIterable.of(o, o + 1)).take(2).take(1).get(0));
    assertEquals((Integer)2, JBIterable.of(1).flatMap(o -> JBIterable.of(o, o + 1)).skip(1).take(1).get(0));
  }

  public void testRangeWithSkipAndTake() {
    Condition<Integer> cond = i -> Math.abs(i - 10) <= 5;
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).skipWhile(not(cond)).takeWhile(cond);
    assertEquals(11, it.size());
    assertEquals(new Integer(5), it.first());
    assertEquals(new Integer(15), it.last());
  }

  public void testSkipWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).skipWhile(LESS_THAN_MOD(10)).take(10);
    assertEquals(Arrays.asList(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), it.toList());
  }

  public void testTakeWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).takeWhile(LESS_THAN_MOD(10)).take(10);
    assertEquals(Arrays.asList(1, 2, 3, 4), it.toList());
  }

  public void testGetAt() {
    JBIterable<Integer> it = JBIterable.of(1, 2, 3, 4);
    assertEquals((Integer)4, it.get(3));
    assertNull(it.get(4));
    assertNull(it.get(5));
    JBIterable<Integer> it2 = JBIterable.generate(1, INCREMENT).take(4);
    assertEquals((Integer)4, it2.get(3));
    assertNull(it2.get(4));
    assertNull(it2.get(5));
  }

  public void testFilterTransformTakeWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).filter(IS_ODD).transform(SQUARE).takeWhile(LESS_THAN(100));
    assertEquals(Arrays.asList(1, 9, 25, 49, 81), it.toList());
    assertEquals(new Integer(1), it.first());
    assertEquals(new Integer(81), it.last());
  }

  public void testFilterTransformSkipWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).filter(IS_ODD).transform(SQUARE).skipWhile(LESS_THAN(100)).take(3);
    assertEquals(Arrays.asList(121, 169, 225), it.toList());
    assertEquals(new Integer(121), it.first());
    assertEquals(new Integer(225), it.last());
  }

  public void testOnce() {
    JBIterable<Integer> it = JBIterable.once(JBIterable.generate(1, INCREMENT).take(3).iterator());
    assertEquals(Arrays.asList(1, 2, 3), it.toList());
    try {
      assertEquals(Arrays.asList(1, 2, 3), it.toList());
      fail();
    }
    catch (UnsupportedOperationException ignored) {
    }
  }

  public void testFlatten() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(3).flatten(i -> i % 2 == 0 ? null : JBIterable.of(i - 1, i));
    assertEquals(Arrays.asList(0, 1, 2, 3), it.toList());
  }

  public void testStatefulFilter() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(5).filter(new JBIterable.StatefulFilter<Integer>() {
      int prev;
      @Override
      public boolean value(Integer integer) {
        boolean b = integer > prev;
        if (b) prev = integer;
        return b;
      }
    });
    assertEquals(Arrays.asList(1, 2, 3, 4, 5), it.toList());
    assertEquals(Arrays.asList(1, 2, 3, 4, 5), it.toList());
  }

  public void testStatefulGenerator() {
    JBIterable<Integer> it = JBIterable.generate(1, FIBONACCI2).take(8);
    assertEquals(Arrays.asList(1, 1, 2, 3, 5, 8, 13, 21), it.toList());
    assertEquals(Arrays.asList(1, 1, 2, 3, 5, 8, 13, 21), it.toList());
  }

  public void testFindIndexReduceMap() {
    JBIterable<Integer> it = JBIterable.of(1, 2, 3, 4, 5);
    assertEquals(15, (int)it.reduce(0, (Integer v, Integer o) -> v + o));
    assertEquals(3, (int)it.find((o)-> o.intValue() == 3));
    assertEquals(2, it.indexOf((o)-> o.intValue() == 3));
    assertEquals(-1, it.indexOf((o)-> o.intValue() == 33));
    assertEquals(Arrays.asList(1, 4, 9, 16, 25), it.map(o -> o * o).toList());
    assertEquals(Arrays.asList(0, 1, 0, 2, 0, 3, 0, 4, 0, 5), it.flatMap(o -> ContainerUtil.list(0, o)).toList());
  }

  public void testJoin() {
    assertEquals("", JBIterable.of().join(", ").reduce("", (a, b) -> a + b));
    assertEquals("a", JBIterable.of("a").join(", ").reduce("", (a, b) -> a + b));
    assertEquals("a, b, c", JBIterable.of("a", "b", "c").join(", ").reduce("", (a, b) -> a + b));
  }

  public void testSplits1() {
    JBIterable<Integer> it = JBIterable.of(1, 2, 3, 4, 5);
    assertEquals(Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4)), it.split(2, true).toList());
    assertEquals(Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4), Arrays.asList(5)), it.split(2, false).toList());

    assertEquals("[[1, 2], [4, 5]]", it.split(OFF, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2], [3], [4, 5]]", it.split(AROUND, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3], [4, 5]]", it.split(AFTER, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2], [3, 4, 5]]", it.split(BEFORE, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3, 4], [5], []]", it.split(AROUND, o -> o == 5).map(o -> o.toList()).toList().toString());
    assertEquals("[[], [1], [2, 3, 4, 5]]", it.split(AROUND, o -> o == 1).map(o -> o.toList()).toList().toString());

    assertEquals("[[], [], [], [], [], []]", it.split(OFF, o -> true).map(o -> o.toList()).toList().toString());
    assertEquals("[[1], [2], [3], [4], [5], []]", it.split(AFTER, o -> true).map(o -> o.toList()).toList().toString());
    assertEquals("[[], [1], [2], [3], [4], [5]]", it.split(BEFORE, o -> true).map(o -> o.toList()).toList().toString());
    assertEquals("[[], [1], [], [2], [], [3], [], [4], [], [5], []]", it.split(AROUND, o -> true).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3, 4, 5]]", it.split(GROUP, o -> true).map(o -> o.toList()).toList().toString());

    assertEquals("[[1, 2, 3, 4, 5]]", it.split(OFF, o -> false).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3, 4, 5]]", it.split(AFTER, o -> false).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3, 4, 5]]", it.split(BEFORE, o -> false).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3, 4, 5]]", it.split(AROUND, o -> false).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3, 4, 5]]", it.split(GROUP, o -> false).map(o -> o.toList()).toList().toString());

    assertEquals(3, it.split(AROUND, o -> o % 3 == 0).size());
    assertEquals(11, it.split(AROUND, o -> true).size());

    assertEquals(it.split(2, false).toList(), it.split(AFTER, o -> o % 2 == 0).map(o -> o.toList()).toList());

    JBIterable<JBIterable<Integer>> statePart = it.split(GROUP, new JBIterable.StatefulFilter<Integer>() {
      int i = 4;

      @Override
      public boolean value(Integer integer) {
        return (i = (i + 2) % 12) - 5 > 0; // 3 positive, 3 negative (+1 +3 +5 : -5 -3 -1)
      }
    });
    assertEquals("[[1, 2, 3], [4, 5]]", statePart.map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3], [4, 5]]", statePart.map(o -> o.toList()).toList().toString());
  }

  public void testSplits2() {
    JBIterable<Integer> it = JBIterable.empty();

    assertEquals("[]", it.split(OFF, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[]", it.split(AROUND, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[]", it.split(AFTER, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[]", it.split(BEFORE, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[]", it.split(GROUP, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());

    it = JBIterable.of(3);

    assertEquals("[[], []]", it.split(OFF, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[], [3], []]", it.split(AROUND, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[3], []]", it.split(AFTER, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[], [3]]", it.split(BEFORE, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[3]]", it.split(GROUP, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());

    it = JBIterable.of(1, 2, 3, 3, 4, 5);

    assertEquals("[[1, 2], [], [4, 5]]", it.split(OFF, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2], [3], [], [3], [4, 5]]", it.split(AROUND, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2, 3], [3], [4, 5]]", it.split(AFTER, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2], [3], [3, 4, 5]]", it.split(BEFORE, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[1, 2], [3, 3], [4, 5]]", it.split(GROUP, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());

    it = JBIterable.of(3, 3, 1, 2, 3, 3);

    assertEquals("[[], [], [1, 2], [], []]", it.split(OFF, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[], [3], [], [3], [1, 2], [3], [], [3], []]", it.split(AROUND, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[3], [3], [1, 2, 3], [3], []]", it.split(AFTER, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[], [3], [3, 1, 2], [3], [3]]", it.split(BEFORE, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());
    assertEquals("[[3, 3], [1, 2], [3, 3]]", it.split(GROUP, o -> o % 3 == 0).map(o -> o.toList()).toList().toString());

    Function<JBIterable<Integer>, JBIterable<JBIterator<Integer>>> cursor = param -> JBIterator.cursor(JBIterator.from(param.iterator()));

    assertEquals("[[], [], [1, 2], [], []]", cursor.fun(it).split(OFF, o -> o.current() % 3 == 0).map(o -> o.map(p -> p.current()).toList()).toList().toString());
    assertEquals("[[], [3], [], [3], [1, 2], [3], [], [3], []]", cursor.fun(it).split(AROUND, o -> o.current() % 3 == 0).map(o -> o.map(p -> p.current()).toList()).toList().toString());
    assertEquals("[[3], [3], [1, 2, 3], [3], []]", cursor.fun(it).split(AFTER, o -> o.current() % 3 == 0).map(o -> o.map(p -> p.current()).toList()).toList().toString());
    assertEquals("[[], [3], [3, 1, 2], [3], [3]]", cursor.fun(it).split(BEFORE, o -> o.current() % 3 == 0).map(o -> o.map(p -> p.current()).toList()).toList().toString());
    assertEquals("[[3, 3], [1, 2], [3, 3]]", cursor.fun(it).split(GROUP, o -> o.current() % 3 == 0).map(o -> o.map(p -> p.current()).toList()).toList().toString());

    assertEquals("[[3, 3], [1, 2], [3, 3]]", it.split(2, true).toList().toString());
    assertEquals("[[3, 3], [1, 2], [3, 3]]", it.split(2).map(o -> o.toList()).toList().toString());
    assertEquals("[[3, 3], [1, 2], [3, 3]]", cursor.fun(it).split(2).map(o -> o.map(p -> p.current()).toList()).toList().toString());
    assertEquals("[[3, 3, 1, 2], [3, 3]]", cursor.fun(it).split(4).map(o -> o.map(p -> p.current()).toList()).toList().toString());
  }

  public void testIterateUnique() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(30);
    assertEquals(it.toList(), it.unique().toList());
    JBIterable<Integer> uniqueMod5 = it.unique((o) -> o % 5);
    assertEquals(Arrays.asList(1, 2, 3, 4, 5), uniqueMod5.toList());
    assertEquals(Arrays.asList(1, 2, 3, 4, 5), uniqueMod5.toList()); // same results again
  }

  // TreeTraversal ----------------------------------------------

  @NotNull
  private static Function<Integer, JBIterable<Integer>> numTraverser(TreeTraversal t) {
    return t.traversal(Functions.fromMap(numbers()));
  }
  @NotNull
  private static Function<Integer, JBIterable<Integer>> numTraverser2(TreeTraversal t) {
    return t.traversal(Functions.fromMap(numbers2()));
  }

  public void testSimplePreOrderDfs() {
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 8, 9, 10, 4, 11, 12, 13), numTraverser(TreeTraversal.PRE_ORDER_DFS).fun(1).toList());
  }

  public void testSimpleInterlacedDfs() {
    assertEquals(Arrays.asList(1, 2, 5, 3, 6, 4, 8, 7, 9, 11, 10, 12, 13), numTraverser(TreeTraversal.INTERLEAVED_DFS).fun(1).toList());
  }

  public void testCyclicInterlacedDfs() {
    Function<Integer, JBIterable<Integer>> traversal = TreeTraversal.INTERLEAVED_DFS.traversal(Functions.fromMap(
      ContainerUtil.<Integer, Collection<Integer>>immutableMapBuilder()
        .put(1, Arrays.asList(1, 2))
        .put(2, Arrays.asList(1, 2, 3))
        .put(3, Arrays.asList()).build()));
    assertEquals(Arrays.asList(1, 1, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 3), traversal.fun(1).takeWhile(UP_TO(3)).toList());
  }

  public void testIndefiniteCyclicInterlacedDfs() {
    Function<Integer, JBIterable<Integer>> traversal = TreeTraversal.INTERLEAVED_DFS.traversal(
      integer -> {
        JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).takeWhile(UP_TO(integer + 1));
        // 1: no repeat
        return it;
        // 2: repeat indefinitely: all seq
        //return JBIterable.generate(it, Functions.id()).flatten(Functions.id());
        // 3: repeat indefinitely: self-cycle
        //return it.append(JBIterable.generate(integer, Functions.id()));
      });
    JBIterable<Integer> counts = JBIterable.generate(1, INCREMENT).transform(integer -> traversal.fun(1).takeWhile(UP_TO(integer)).size());
    // 1: no repeat
    assertEquals(Arrays.asList(1, 4, 13, 39, 117, 359, 1134, 3686, 12276, 41708), counts.take(10).toList());
    // 2: repeat all seq
    //assertEquals(Arrays.asList(1, 4, 19, 236), counts.take(4).toList());
    // 2: repeat self-cycle
    //assertEquals(Arrays.asList(1, 4, 19, 236), counts.take(4).toList());
  }

  public void testTreeBacktraceSimple() {
    JBIterable<Integer> dfs = numTraverser2(TreeTraversal.PRE_ORDER_DFS).fun(1);
    JBIterable<Integer> bfs = numTraverser2(TreeTraversal.TRACING_BFS).fun(1);
    JBIterable<Integer> postDfs = numTraverser2(TreeTraversal.POST_ORDER_DFS).fun(1);

    TreeTraversal.TracingIt<Integer> it1 = dfs.typedIterator();
    assertEquals(new Integer(37), it1.skipWhile(Conditions.notEqualTo(37)).next());

    TreeTraversal.TracingIt<Integer> it2 = bfs.typedIterator();
    assertEquals(new Integer(37), it2.skipWhile(Conditions.notEqualTo(37)).next());

    TreeTraversal.TracingIt<Integer> it3 = postDfs.typedIterator();
    assertEquals(new Integer(37), it3.skipWhile(Conditions.notEqualTo(37)).next());

    assertEquals(Arrays.asList(37, 12, 4, 1), it1.backtrace().toList());
    assertEquals(Arrays.asList(37, 12, 4, 1), it2.backtrace().toList());
    assertEquals(Arrays.asList(37, 12, 4, 1), it3.backtrace().toList());

    assertEquals(new Integer(12), it1.parent());
    assertEquals(new Integer(12), it2.parent());
    assertEquals(new Integer(12), it3.parent());
  }

  public void testTreeBacktraceSingle() {
    Integer root = 123;
    JBTreeTraverser<Integer> traverser = new JBTreeTraverser<Integer>(Functions.constant(null)).withRoot(root);
    JBIterable<Integer> dfs = traverser.traverse(TreeTraversal.PRE_ORDER_DFS);
    JBIterable<Integer> bfs = traverser.traverse(TreeTraversal.TRACING_BFS);
    JBIterable<Integer> postDfs = traverser.traverse(TreeTraversal.POST_ORDER_DFS);

    TreeTraversal.TracingIt<Integer> it1 = dfs.typedIterator();
    assertEquals(root, it1.next());

    TreeTraversal.TracingIt<Integer> it2 = bfs.typedIterator();
    assertEquals(root, it2.next());

    TreeTraversal.TracingIt<Integer> it3 = postDfs.typedIterator();
    assertEquals(root, it3.next());

    assertEquals(Arrays.asList(root), it1.backtrace().toList());
    assertEquals(Arrays.asList(root), it2.backtrace().toList());
    assertEquals(Arrays.asList(root), it3.backtrace().toList());

    assertEquals(null, it1.parent());
    assertEquals(null, it2.parent());
    assertEquals(null, it3.parent());
  }

  public void testTreeBacktraceTransformed() {
    JBIterable<String> dfs = numTraverser2(TreeTraversal.PRE_ORDER_DFS).fun(1).transform(Functions.TO_STRING());
    JBIterable<String> bfs = numTraverser2(TreeTraversal.TRACING_BFS).fun(1).transform(Functions.TO_STRING());

    TreeTraversal.TracingIt<String> it1 = dfs.typedIterator();
    it1.skipWhile(Conditions.notEqualTo("37")).next();

    TreeTraversal.TracingIt<String> it2 = bfs.typedIterator();
    it2.skipWhile(Conditions.notEqualTo("37")).next();

    assertEquals(Arrays.asList("37", "12", "4", "1"), it1.backtrace().toList());
    assertEquals(Arrays.asList("37", "12", "4", "1"), it2.backtrace().toList());

    assertEquals("12", it1.parent());
    assertEquals("12", it2.parent());
  }

  public void testSimplePostOrderDfs() {
    assertEquals(Arrays.asList(5, 6, 7, 2, 8, 9, 10, 3, 11, 12, 13, 4, 1), numTraverser(TreeTraversal.POST_ORDER_DFS).fun(1).toList());
  }

  public void testSimpleBfs() {
    assertEquals(JBIterable.generate(1, INCREMENT).take(37).toList(), numTraverser2(TreeTraversal.PLAIN_BFS).fun(1).toList());
  }

  public void testSimpleBfsLaziness() {
    List<Integer> result = simpleTraverseExpand(TreeTraversal.PLAIN_BFS);
    assertEquals(JBIterable.of(1, 2, 2, 4, 4, 4, 4, 8, 8, 8, 8, 8, 8, 8, 8).toList(), result);
  }

  public void testSimplePreDfsLaziness() {
    List<Integer> result = simpleTraverseExpand(TreeTraversal.PRE_ORDER_DFS);
    assertEquals(JBIterable.of(1, 2, 4, 8, 8, 4, 8, 8, 2, 4, 8, 8, 4, 8, 8).toList(), result);
  }

  @NotNull
  public List<Integer> simpleTraverseExpand(TreeTraversal traversal) {
    List<Integer> result = ContainerUtil.newArrayList();
    JBIterable<List<Integer>> iter = traversal.traversal((Function<List<Integer>, Iterable<List<Integer>>>)integers ->
      JBIterable.from(integers).skip(1).transform(WRAP_TO_LIST)).fun(ContainerUtil.newArrayList(1));
    for (List<Integer> integers : iter) {
      Integer cur = integers.get(0);
      result.add(cur);
      if (cur > 4) continue;
      integers.add(cur*2);
      integers.add(cur*2);
    }
    return result;
  }

  public void testTracingBfsLaziness() {
    List<Integer> result = ContainerUtil.newArrayList();
    TreeTraversal.TracingIt<List<Integer>> it = TreeTraversal.TRACING_BFS.traversal((Function<List<Integer>, Iterable<List<Integer>>>)integers ->
        JBIterable.from(integers).skip(1).transform(WRAP_TO_LIST)).fun(ContainerUtil.newArrayList(1)).typedIterator();
    while (it.advance()) {
      Integer cur = it.current().get(0);
      result.add(cur);
      assertEquals(JBIterable.generate(cur, DIV_2).takeWhile(IS_POSITIVE).toList(), it.backtrace().transform(integers -> integers.get(0)) .toList());
      if (cur > 4) continue;
      it.current().add(cur*2);
      it.current().add(cur*2);
    }

    assertEquals(JBIterable.of(1, 2, 2, 4, 4, 4, 4, 8, 8, 8, 8, 8, 8, 8, 8).toList(), result);
  }

  public void testTraverseUnique() {
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 8, 9, 10, 4, 11, 12, 13), numTraverser(TreeTraversal.PRE_ORDER_DFS.unique()).fun(1).toList());
    JBIterable<Integer> uniqueMod5 = numTraverser(TreeTraversal.PRE_ORDER_DFS.unique((Integer o) -> o % 5)).fun(1);
    assertEquals(Arrays.asList(1, 2, 5, 3, 9), uniqueMod5.toList());
    assertEquals(Arrays.asList(1, 2, 5, 3, 9), uniqueMod5.toList()); // same results again

    JBIterable<Integer> uniqueMod7 = numTraverser(TreeTraversal.PRE_ORDER_DFS.unique((Integer o) -> o % 5).unique((Integer o) -> o % 7)).fun(1);
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 4), uniqueMod7.toList());

    assertEquals(JBIterable.generate(1, INCREMENT).take(37).toList(), numTraverser2(TreeTraversal.PLAIN_BFS.unique()).fun(1).toList());
    assertEquals(JBIterable.generate(1, INCREMENT).take(37).toList(), numTraverser2(TreeTraversal.PLAIN_BFS.unique().unique()).fun(1).toList());
  }

  // GuidedTraversal ----------------------------------------------

  @NotNull
  private static TreeTraversal.GuidedIt.Guide<Integer> newGuide(@NotNull final TreeTraversal traversal) {
    return it -> {
      if (traversal == TreeTraversal.PRE_ORDER_DFS) {
        it.queueNext(it.curChild).result(it.curChild);
      }
      else if (traversal == TreeTraversal.POST_ORDER_DFS) {
        it.queueNext(it.curChild).result(it.curChild == null ? it.curParent : null);
      }
      else if (traversal == TreeTraversal.PLAIN_BFS) {
        it.queueLast(it.curChild).result(it.curChild);
      }
    };
  }

  public void testGuidedDfs() {
    verifyGuidedTraversal(TreeTraversal.PRE_ORDER_DFS);
    verifyGuidedTraversal(TreeTraversal.POST_ORDER_DFS);
    verifyGuidedTraversal(TreeTraversal.PLAIN_BFS);
  }

  private static void verifyGuidedTraversal(TreeTraversal traversal) {
    assertEquals(numTraverser2(TreeTraversal.GUIDED_TRAVERSAL(newGuide(traversal))).fun(1).toList(),
                 numTraverser2(traversal).fun(1).toList());
  }


  // FilteredTraverser ----------------------------------------------

  @NotNull
  private static JBTreeTraverser<Integer> numberTraverser() {
    return new JBTreeTraverser<>(Functions.fromMap(numbers()));
  }

  @NotNull
  public JBTreeTraverser<TextRange> rangeTraverser() {
    return new JBTreeTraverser<>(
      r -> r.getLength() < 4 ? JBIterable.empty() : JBIterable.generate(r.getStartOffset(), i -> i += r.getLength() / 4)
        .takeWhile(i -> i < r.getEndOffset())
        .map(i -> TextRange.from(i, r.getLength() / 4)));
  }

  public void testSimpleFilter() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).filter(IS_ODD).toList());
  }

  public void testSimpleExpand() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 2, 3, 8, 9, 10, 4), t.withRoot(1).expand(IS_ODD).toList());
  }

  public void testExpandFilter() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 3, 9), t.withRoot(1).expand(IS_ODD).filter(IS_ODD).toList());
    assertEquals(Arrays.asList(1, 3, 9), t.withRoot(1).expandAndFilter(IS_ODD).toList());
  }

  public void testSkipExpandedDfs() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(2, 8, 9, 10, 4), t.withRoot(1).expand(IS_ODD).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testOnRange() {
    assertEquals(13, numberTraverser().withRoot(1).onRange(o -> true).traverse().size());
    JBTreeTraverser<TextRange> ranges = rangeTraverser();
    assertEquals(5, ranges.withRoot(TextRange.from(0, 8)).traverse().size());
    assertEquals(Arrays.asList("(0,64)", "(16,32)", "(28,32)", "(29,30)", "(30,31)", "(31,32)", "(32,48)", "(32,36)", "(32,33)", "(33,34)"),
                 ranges.withRoot(TextRange.from(0, 64))
                   .onRange(r -> r.intersects(30, 33))
                   .preOrderDfsTraversal().map(Object::toString).toList());
  }

  public void testRangeChildrenLeavesDfs() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(5, 6, 3, 11, 12, 13), t.withRoot(1).regard(not(inRange(7, 10))).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testRangeChildrenLeavesBfs() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(5, 6, 3, 11, 12, 13), t.withRoot(1).regard(not(inRange(7, 10))).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testHideOneNodeDfs() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 4, 11, 12, 13), t.withRoot(1).expandAndFilter(x -> x != 3).traverse(TreeTraversal.PRE_ORDER_DFS).toList());
  }

  public void testHideOneNodeCompletelyBfs() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 2, 4, 5, 6, 7, 11, 12, 13), t.withRoot(1).expandAndFilter(x -> x != 3).traverse(TreeTraversal.PLAIN_BFS).toList());
  }

  public void testSkipExpandedCompletelyBfs() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(2, 4, 8, 9, 10), t.withRoot(1).expand(IS_ODD).traverse(TreeTraversal.LEAVES_BFS).toList());
  }

  public void testExpandSkipFilterReset() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).expand(IS_ODD).
      withTraversal(TreeTraversal.LEAVES_DFS).reset().filter(IS_ODD).toList());
  }

  public void testForceExlcudeReset() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 2, 6, 4, 12), t.withRoot(1).forceIgnore(IS_ODD).reset().toList());
  }

  public void testForceSkipReset() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 2, 6, 8, 10, 4, 12), t.withRoot(1).forceDisregard(IS_ODD).reset().toList());
  }

  public void testForceSkipLeavesDfs() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(6, 8, 10, 12), t.withRoot(1).forceDisregard(IS_ODD).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testFilterChildren() {
    JBTreeTraverser<Integer> t = numberTraverser();
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), t.withRoot(1).regard(IS_ODD).toList());
  }

  public void testEndlessGraph() {
    JBTreeTraverser<Integer> t = new JBTreeTraverser<>(k -> JBIterable.generate(k, INCREMENT).transform(SQUARE).take(3));
    assertEquals(Arrays.asList(1, 1, 4, 9, 1, 4, 9, 16, 25, 36, 81), t.withRoot(1).bfsTraversal().take(11).toList());
  }

  public void testEndlessGraphParents() {
    JBTreeTraverser<Integer> t = new JBTreeTraverser<>(k -> JBIterable.generate(1, k, FIBONACCI).skip(2).take(3));
    TreeTraversal.TracingIt<Integer> it = t.withRoot(1).preOrderDfsTraversal().skip(20).typedIterator();
    TreeTraversal.TracingIt<Integer> cursor = JBIterator.cursor(it).first();
    assertNotNull(cursor);
    assertSame(cursor, it);
    assertEquals(Arrays.asList(21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1), cursor.backtrace().toList());
  }

  public void testEdgeFilter() {
    JBTreeTraverser<Integer> t = numberTraverser();
    JBIterable<Integer> it = t.regard(new FilteredTraverserBase.EdgeFilter<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return (integer / edgeSource) % 2 == 0;
      }
    }).withRoot(1).traverse();
    assertEquals(Arrays.asList(1, 2, 5, 8, 10, 4, 11), it.toList());
    assertEquals(Arrays.asList(1, 2, 5, 8, 10, 4, 11), it.toList());
  }

  public void testStatefulChildFilter() {
    JBTreeTraverser<Integer> t = numberTraverser();
    class F extends JBIterable.StatefulFilter<Integer> {
      int count;
      boolean value;
      F(boolean initialVal) { value = initialVal; }

      public boolean value(Integer integer) {
        return count ++ > 0 == value;
      }
    }

    JBIterable<Integer> it = t.regard(new F(true)).withRoot(1).traverse();
    assertEquals(Arrays.asList(1, 5, 6, 7, 3, 9, 10, 4, 12, 13), it.toList());
    assertEquals(Arrays.asList(1, 5, 6, 7, 3, 9, 10, 4, 12, 13), it.toList());
    assertEquals(it.toList(), t.forceDisregard(new F(false)).withRoot(1).reset().traverse().toList());
  }

}
