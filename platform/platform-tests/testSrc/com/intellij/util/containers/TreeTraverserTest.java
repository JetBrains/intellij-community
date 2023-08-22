// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;
import java.util.HashMap;
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
    return Map.of(
      1, Arrays.asList(2, 3, 4),
      2, Arrays.asList(5, 6, 7),
      3, Arrays.asList(8, 9, 10),
      4, Arrays.asList(11, 12, 13));
  }

  private static Map<Integer, Collection<Integer>> numbers2() {
    return Map.ofEntries(
      Map.entry(1, Arrays.asList(2, 3, 4)),
      Map.entry(2, Arrays.asList(5, 6, 7)),
      Map.entry(3, Arrays.asList(8, 9, 10)),
      Map.entry(4, Arrays.asList(11, 12, 13)),
      Map.entry(5, Arrays.asList(14, 15, 16)),
      Map.entry(6, Arrays.asList(17, 18, 19)),
      Map.entry(7, Arrays.asList(20, 21, 22)),
      Map.entry(8, Arrays.asList(23, 24, 25)),
      Map.entry(9, Arrays.asList(26, 27, 28)),
      Map.entry(10, Arrays.asList(29, 30, 31)),
      Map.entry(11, Arrays.asList(32, 33, 34)),
      Map.entry(12, Arrays.asList(35, 36, 37)));
  }

  private static final Function<Integer, Integer> ASSERT_NUMBER = o -> {
    if (o instanceof Number) return o;
    throw new AssertionError(String.valueOf(o));
  };

  private static final Condition<Integer> IS_ODD = integer -> integer.intValue() % 2 == 1;

  private static final Condition<Integer> IS_POSITIVE = integer -> integer.intValue() > 0;

  private static Condition<Integer> inRange(int s, int e) {
    return integer -> s <= integer && integer <= e;
  }

  private static final Function<Integer, List<Integer>> WRAP_TO_LIST = integer -> new SmartList<>(integer);

  private static final Function<Integer, Integer> DIV_2 = k -> k / 2;

  private static final Function<Integer, Integer> INCREMENT = k -> k + 1;

  private static final Function<Integer, Integer> SQUARE = k -> k * k;

  private static final PairFunction<Integer, Integer, Integer> FIBONACCI = (k1, k2) -> k2 + k1;

  private static final Function<Integer, Integer> FIBONACCI2 = new JBIterable.SFun<>() {
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
  private static <E> JBIterable.SCond<E> UP_TO(final E o) {
    return new JBIterable.SCond<>() {
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
    assertEquals(Integer.valueOf(1), it.current());
    assertTrue(it.hasNext());
    assertTrue(it.hasNext());
    assertEquals(Integer.valueOf(2), it.next());   // advance->2
    assertEquals(Integer.valueOf(2), it.current());
    assertEquals(Integer.valueOf(2), it.current());
    assertTrue(it.advance());                  // advance->3
    assertEquals(Integer.valueOf(4), it.next());   // advance->4
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
    assertEquals(Integer.valueOf(1), it.current());
    assertFalse(it.hasNext());
    assertEquals(Integer.valueOf(1), it.current());
  }

  public void testCursorIterableContract() {
    List<Integer> list = new ArrayList<>();
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
    List<Integer> list = new ArrayList<>();
    while (it.advance()) {
      it.hasNext();
      list.add(it.current().current());
    }
    assertEquals(orig.toList(), list);
  }

  public void testCursorTransform() {
    JBIterable<Integer> orig = JBIterable.generate(1, INCREMENT).take(5);

    List<Integer> expected = List.of(1, 2, 3, 4, 5);
    List<Integer> expectedOdd = List.of(1, 3, 5);
    assertEquals(expected, JBIterator.cursor(JBIterator.from(orig.iterator())).transform(o -> o.current()).toList());
    assertEquals(expected.size(), JBIterator.cursor(JBIterator.from(orig.iterator())).last().current().intValue());
    assertEquals(expectedOdd, JBIterator.cursor(JBIterator.from(orig.iterator())).transform(o -> o.current()).filter(IS_ODD).toList());
    assertEquals(expectedOdd, JBIterator.cursor(JBIterator.from(orig.iterator())).filter(o -> IS_ODD.value(o.current())).transform(o -> o.current()).toList());
    assertEquals(expected.subList(0, 4), JBIterator.cursor(JBIterator.from(orig.iterator())).filter(o -> o.hasNext()).transform(o -> o.current()).toList());
  }

  public void testIteratorContractsSkipAndStop() {
    final AtomicInteger count = new AtomicInteger(0);
    JBIterator<Integer> it = new JBIterator<>() {

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

  public void testIterableOfNulls() {
    Object nil = null;
    assertEquals("[]", JBIterable.of(nil).toList().toString());
    assertEquals("[null, null, null]", JBIterable.of(nil, nil, nil).toList().toString());
    assertEquals("[null, null, null]", JBIterable.of(ContainerUtil.ar(nil, nil, nil)).toList().toString());
    assertEquals("[null, null, null]", JBIterable.from(Arrays.asList(nil, nil, nil)).toList().toString());
    assertEquals("[]", JBIterable.generate(null, x -> null).toList().toString());
    assertEquals("[42]", JBIterable.generate(42, x -> null).toList().toString());
  }

  public void testSingleElement() {
    JBIterable<String> it = JBIterable.of("42");
    assertEquals(1, it.size());
    assertEquals("42", it.first());
    assertEquals("42", it.last());
    assertEquals("42", it.single());
    assertEquals("[42, 42]", it.append(it).toList().toString());
    assertEquals("[42, 42]", it.repeat(2).toList().toString());
    assertEquals("[42, 42, 48, 48]", it.append("42").append(Arrays.asList("48", "48")).toList().toString());
    assertEquals("[42, 42, 48, 48, 49]", it.append("42").append(Arrays.asList("48", "48")).append("49").toList().toString());
    assertEquals("[42, 42, 48, 48, 49]", it.append("42").append(JBIterable.of("48").append("48")).append("49").toList().toString());
  }

  public void testSingleElementWithList() {
    Object element = Arrays.asList(1, 2);
    JBIterable<Object> iterable = JBIterable.of(element);
    assertEquals(1, iterable.size());
    assertFalse(iterable.isEmpty());
    assertEquals(element, iterable.first());
    assertEquals(element, iterable.last());
    assertEquals(element, iterable.get(0));
    assertNull(iterable.get(1));
    assertTrue(iterable.contains(element));
    assertFalse(iterable.contains(1));
    assertEquals("[[1, 2]]", iterable.toList().toString());
    assertEquals("[[1, 2]]", iterable.toSet().toString());
    assertEquals("[[1, 2]]", Arrays.asList(iterable.toArray(new Object[0])).toString());
    assertEquals("[[1, 2]]", iterable.addAllTo(new ArrayListSet<>()).toString());
  }

  public void testSingleElementWithEmptyList() {
    Object element = Collections.emptyList();
    JBIterable<Object> iterable = JBIterable.of(element);
    assertFalse(iterable.isEmpty());
    assertEquals(1, iterable.size());
    assertEquals(element, iterable.first());
    assertEquals(element, iterable.last());
    assertEquals(element, iterable.get(0));
    assertNull(iterable.get(1));
    assertTrue(iterable.contains(element));
    assertFalse(iterable.contains(null));
    assertEquals("[[]]", iterable.toList().toString());
    assertEquals("[[]]", iterable.toSet().toString());
    assertEquals("[[]]", Arrays.asList(iterable.toArray(new Object[0])).toString());
    assertEquals("[[]]", iterable.addAllTo(new ArrayListSet<>()).toString());
  }

  public void testFirstLastSingle() {
    assertNull(JBIterable.empty().first());
    assertNull(JBIterable.empty().last());
    assertNull(JBIterable.empty().single());

    assertEquals("a", JBIterable.generate("a", o -> o + "a").first());
    assertEquals("aaa", JBIterable.generate("a", o -> o + "a").take(3).last());
    assertEquals("a", JBIterable.generate("a", o -> o + "a").take(1).single());
    assertNull(JBIterable.generate("a", o -> o + "a").take(2).single());

    assertEquals("a", JBIterable.from(Arrays.asList("a", "aa", "aaa")).first());
    assertEquals("aaa", JBIterable.from(Arrays.asList("a", "aa", "aaa")).last());
    assertEquals("a", JBIterable.of("a").single());
    assertNull(JBIterable.of("a", "aa", "aaa").single());
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
    assertEquals(Integer.valueOf(11), it.first());
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
    assertEquals(Integer.valueOf(5), it.first());
    assertEquals(Integer.valueOf(15), it.last());
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
    assertEquals(Integer.valueOf(1), it.first());
    assertEquals(Integer.valueOf(81), it.last());
  }

  public void testFilterTransformSkipWhile() {
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).filter(IS_ODD).transform(SQUARE).skipWhile(LESS_THAN(100)).take(3);
    assertEquals(Arrays.asList(121, 169, 225), it.toList());
    assertEquals(Integer.valueOf(121), it.first());
    assertEquals(Integer.valueOf(225), it.last());
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
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(5).filter(new JBIterable.SCond<>() {
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

  public void testStatefulFilterWithSet() {
    Integer base = 5;
    JBIterable<Integer> it = JBIterable.generate(1, INCREMENT).take(10).filter(new JBIterable.SCond<>() {
      IntSet visited; // MUST NOT be initialized here

      @Override
      public boolean value(Integer integer) {
        if (visited == null) {
          visited = new IntOpenHashSet();
        }
        return visited.add(integer % base);
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
    assertEquals(Arrays.asList(0, 1, 0, 2, 0, 3, 0, 4, 0, 5), it.flatMap(o -> Arrays.asList(0, o)).toList());
  }

  public void testJoin() {
    assertNull(JBIterable.<String>of().join(", ").reduce((a, b) -> a + b));
    assertEquals("", JBIterable.of().join(", ").reduce("", (a, b) -> a + b));
    assertEquals("a", JBIterable.of("a").join(", ").reduce((a, b) -> a + b));
    assertEquals("a, b, c", JBIterable.of("a", "b", "c").join(", ").reduce((a, b) -> a + b));
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

    JBIterable<JBIterable<Integer>> statePart = it.split(GROUP, new JBIterable.SCond<>() {
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

  public void testSort() {
    JBIterable<Integer> it1 = JBIterable.generate(1, INCREMENT).take(30);
    JBIterable<Integer> it2 = JBIterable.generate(30, o -> o - 1).take(30).sort(Integer::compareTo);
    assertEquals(it1.toList(), it2.unique().toList());
  }

  // TreeTraversal ----------------------------------------------

  @NotNull
  private static JBTreeTraverser<Integer> numTraverser() {
    return new JBTreeTraverser<>(Functions.compose(ASSERT_NUMBER, Functions.fromMap(numbers()))).withRoot(1);
  }

  @NotNull
  private static JBTreeTraverser<Integer> num2Traverser() {
    return new JBTreeTraverser<>(Functions.compose(ASSERT_NUMBER, Functions.fromMap(numbers2()))).withRoot(1);
  }


  @NotNull
  private static JBIterable<TreeTraversal> allTraversals() {
    JBIterable<TreeTraversal> result = JBIterable.of(TreeTraversal.class.getDeclaredFields())
      .filter(o -> Modifier.isStatic(o.getModifiers()) && Modifier.isPublic(o.getModifiers()))
      .map(o -> { try { return o.get(null); } catch (IllegalAccessException e) { throw new AssertionError(e); } })
      .filter(TreeTraversal.class)
      .sort(Comparator.comparing(Object::toString))
      .collect();
    assertEquals("[BI_ORDER_DFS, INTERLEAVED_DFS, LEAVES_BFS, LEAVES_DFS," +
                 " PLAIN_BFS, POST_ORDER_DFS, PRE_ORDER_DFS, TRACING_BFS]",
                 result.toList().toString());
    return result;
  }


  public void testTraverserOfNulls() {
    JBIterable<TreeTraversal> traversals = allTraversals();

    Object nil = null;
    JBTreeTraverser<Object> t1 = JBTreeTraverser.from(o -> JBIterable.of(nil, nil)).withRoots(Arrays.asList(nil));
    assertEquals("""
                   BI_ORDER_DFS [null, null]
                   INTERLEAVED_DFS [null]
                   LEAVES_BFS [null]
                   LEAVES_DFS [null]
                   PLAIN_BFS [null]
                   POST_ORDER_DFS [null]
                   PRE_ORDER_DFS [null]
                   TRACING_BFS [null]""",
                 StringUtil.join(traversals.map(o -> o + " " + t1.traverse(o).toList()), "\n"));

    JBTreeTraverser<Object> t2 = JBTreeTraverser.from(o -> JBIterable.of(nil, nil)).withRoots(Arrays.asList(42));
    assertEquals("""
                   BI_ORDER_DFS [42, null, null, null, null, 42]
                   INTERLEAVED_DFS [42, null, null]
                   LEAVES_BFS [null, null]
                   LEAVES_DFS [null, null]
                   PLAIN_BFS [42, null, null]
                   POST_ORDER_DFS [null, null, 42]
                   PRE_ORDER_DFS [42, null, null]
                   TRACING_BFS [42, null]""",
                 StringUtil.join(traversals.map(o -> o + " " + t2.traverse(o).toList()), "\n"));
  }

  public void testSimplePreOrderDfs() {
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 8, 9, 10, 4, 11, 12, 13), numTraverser().toList());
  }

  public void testSimpleBiOrderDfs() {
    assertEquals(Arrays.asList(1, 2, 5, 5, 6, 6, 7, 7, 2, 3, 8, 8, 9, 9, 10, 10, 3, 4, 11, 11, 12, 12, 13, 13, 4, 1),
                 numTraverser().withTraversal(TreeTraversal.BI_ORDER_DFS).toList());
  }

  public void testSimpleBiOrderDfs2Roots() {
    assertEquals(Arrays.asList(2, 5, 5, 6, 6, 7, 7, 2, 3, 8, 8, 9, 9, 10, 10, 3, 4, 11, 11, 12, 12, 13, 13, 4), TreeTraversal.BI_ORDER_DFS.traversal(numbers().get(1), Functions.fromMap(numbers())).toList());
  }

  public void testHarderBiOrderDfs() {
    StringBuilder sb = new StringBuilder();
    TreeTraversal.TracingIt<Integer> it = numTraverser().withTraversal(TreeTraversal.BI_ORDER_DFS).traverse().typedIterator();
    while (it.advance()) {
      if (sb.length() != 0) sb.append(", ");
      it.hasNext();
      sb.append(it.current()).append(it.isDescending() ? "↓" : "↑");
    }
    assertEquals("1↓, 2↓, 5↓, 5↑, 6↓, 6↑, 7↓, 7↑, 2↑, 3↓, 8↓, 8↑, 9↓, 9↑, 10↓, 10↑, 3↑, 4↓, 11↓, 11↑, 12↓, 12↑, 13↓, 13↑, 4↑, 1↑", sb.toString());
  }

  public void testSimpleInterlacedDfs() {
    assertEquals(Arrays.asList(1, 2, 5, 3, 6, 4, 8, 7, 9, 11, 10, 12, 13),
                 numTraverser().withTraversal(TreeTraversal.INTERLEAVED_DFS).toList());
  }

  public void testCyclicInterlacedDfs() {
    Function<Integer, JBIterable<Integer>> traversal = TreeTraversal.INTERLEAVED_DFS.traversal(Functions.fromMap(
      Map.of(
        1, Arrays.asList(1, 2),
        2, Arrays.asList(1, 2, 3),
        3, Arrays.asList())));
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
    JBIterable<Integer> dfs = num2Traverser().withTraversal(TreeTraversal.PRE_ORDER_DFS).traverse();
    JBIterable<Integer> bfs = num2Traverser().withTraversal(TreeTraversal.TRACING_BFS).traverse();
    JBIterable<Integer> postDfs = num2Traverser().withTraversal(TreeTraversal.POST_ORDER_DFS).traverse();

    TreeTraversal.TracingIt<Integer> it1 = dfs.typedIterator();
    assertEquals(Integer.valueOf(37), it1.skipWhile(Conditions.notEqualTo(37)).next());

    TreeTraversal.TracingIt<Integer> it2 = bfs.typedIterator();
    assertEquals(Integer.valueOf(37), it2.skipWhile(Conditions.notEqualTo(37)).next());

    TreeTraversal.TracingIt<Integer> it3 = postDfs.typedIterator();
    assertEquals(Integer.valueOf(37), it3.skipWhile(Conditions.notEqualTo(37)).next());

    assertEquals(Arrays.asList(37, 12, 4, 1), it1.backtrace().toList());
    assertEquals(Arrays.asList(37, 12, 4, 1), it2.backtrace().toList());
    assertEquals(Arrays.asList(37, 12, 4, 1), it3.backtrace().toList());

    assertTrue(it1.hasNext());
    assertFalse(it2.hasNext());
    assertTrue(it3.hasNext());

    assertEquals(Arrays.asList(37, 12, 4, 1), it1.backtrace().toList());
    assertEquals(Arrays.asList(37, 12, 4, 1), it2.backtrace().toList());
    assertEquals(Arrays.asList(37, 12, 4, 1), it3.backtrace().toList());

    assertEquals(Integer.valueOf(12), it1.parent());
    assertEquals(Integer.valueOf(12), it2.parent());
    assertEquals(Integer.valueOf(12), it3.parent());
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

    assertNull(it1.parent());
    assertNull(it2.parent());
    assertNull(it3.parent());
  }

  public void testTreeBacktraceTransformed() {
    JBIterable<String> dfs = num2Traverser().withTraversal(TreeTraversal.PRE_ORDER_DFS).traverse().map(Functions.TO_STRING());
    JBIterable<String> bfs = num2Traverser().withTraversal(TreeTraversal.TRACING_BFS).traverse().map(Functions.TO_STRING());

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
    assertEquals(Arrays.asList(5, 6, 7, 2, 8, 9, 10, 3, 11, 12, 13, 4, 1),
                 numTraverser().withTraversal(TreeTraversal.POST_ORDER_DFS).toList());
  }

  public void testSimpleBfs() {
    assertEquals(JBIterable.generate(1, INCREMENT).take(37).toList(),
                 num2Traverser().withTraversal(TreeTraversal.PLAIN_BFS).toList());
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
    List<Integer> result = new ArrayList<>();
    Function<List<Integer>, Iterable<List<Integer>>> function = integers -> JBIterable.from(integers).skip(1).transform(WRAP_TO_LIST);
    JBIterable<List<Integer>> iter = traversal.traversal(function).fun(new SmartList<>(1));
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
    List<Integer> result = new ArrayList<>();
    TreeTraversal.TracingIt<List<Integer>> it = TreeTraversal.TRACING_BFS.traversal((Function<List<Integer>, Iterable<List<Integer>>>)integers ->
        JBIterable.from(integers).skip(1).transform(WRAP_TO_LIST)).fun(new SmartList<>(1)).typedIterator();
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
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 8, 9, 10, 4, 11, 12, 13), numTraverser().unique().toList());
    JBTreeTraverser<Integer> t0 = numTraverser().unique(o -> o % 5);
    assertEquals(Arrays.asList(1, 2, 5, 3, 9), t0.toList());
    assertEquals(Arrays.asList(1, 2, 5, 3, 9), t0.toList()); // same results again

    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 4), numTraverser().unique(o -> o % 7).toList());

    JBIterable<Integer> t1 = numTraverser().unique(o -> o % 5).unique(o -> o % 7).traverse();
    JBIterable<Integer> t2 = numTraverser().unique(o -> o % 7).unique(o -> o % 5).traverse();
    assertEquals(Arrays.asList(1, 2, 5, 3), t1.toList());
    assertEquals(Arrays.asList(1, 2, 5, 3, 4), t2.toList());

    TreeTraversal preOrder = TreeTraversal.PRE_ORDER_DFS;
    assertEquals(t1.toList(), numTraverser().traverse(preOrder.unique(o -> ((int)o) % 5).unique(o -> ((int)o) % 7)).toList());
    assertEquals(t2.toList(), numTraverser().traverse(preOrder.unique(o -> ((int)o) % 7).unique(o -> ((int)o) % 5)).toList());

    assertEquals(JBIterable.generate(1, INCREMENT).take(37).toList(),
                 num2Traverser().withTraversal(TreeTraversal.PLAIN_BFS.unique()).toList());
    assertEquals(JBIterable.generate(1, INCREMENT).take(37).toList(),
                 num2Traverser().withTraversal(TreeTraversal.PLAIN_BFS.unique().unique()).toList());
  }

  public void testTraverseCached() {
    HashMap<Integer, Collection<Integer>> map1 = new HashMap<>(numbers());
    JBTreeTraverser<Integer> traverser = JBTreeTraverser.from(Functions.compose(ASSERT_NUMBER, a -> map1.remove(a))).withRoot(1);
    JBTreeTraverser<Integer> cached = traverser.cached();
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 3, 8, 9, 10, 4, 11, 12, 13), cached.toList());
    assertEquals(cached.toList(), cached.toList());
    assertEquals(Arrays.asList(1), traverser.toList());
  }

  public void testTraverseMap() {
    Condition<String> notEmpty = o -> !o.isEmpty();
    Condition<String> isThirteen = o -> "13".equals(o);
    JBTreeTraverser<Integer> t = numTraverser().filter(IS_ODD).regard(IS_POSITIVE);
    JBTreeTraverser<String> mappedA = t.map(String::valueOf, Integer::parseInt);
    JBTreeTraverser<String> mappedB = t.map(String::valueOf);
    JBTreeTraverser<Integer> mapped2A = mappedA.map(Integer::parseInt);
    JBTreeTraverser<Integer> mapped2B = mappedB.map(Integer::parseInt);
    JBTreeTraverser<Integer> mapped3A = mappedA.expand(notEmpty).regard(notEmpty).forceDisregard(isThirteen).map(o -> Integer.parseInt(o));
    JBTreeTraverser<Integer> mapped3B = mappedB.expand(notEmpty).regard(notEmpty).forceDisregard(isThirteen).map(o -> Integer.parseInt(o));
    JBTreeTraverser<String> mapped4A = mapped3B.map(String::valueOf).map(Integer::parseInt).map(String::valueOf);
    JBTreeTraverser<String> mapped4B = mapped3B.map(String::valueOf).map(Integer::parseInt).map(String::valueOf);

    assertFalse(mappedA.children("1").isEmpty());
    assertTrue(mappedB.children("1").isEmpty()); // not supported in irreversible mapped trees

    assertEquals(Arrays.asList("1", "5", "7", "3", "9", "11", "13"), mappedA.toList());
    assertEquals(Arrays.asList("1", "5", "7", "3", "9", "11", "13"), mappedB.toList());
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), mapped2A.toList());
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), mapped2B.toList());
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), mapped2A.reset().toList());
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), mapped2B.reset().toList());
    assertEquals(t.toList(), mapped2A.toList());
    assertEquals(t.toList(), mapped2B.toList());
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11), mapped3A.toList());
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11), mapped3B.toList());
    assertEquals(Arrays.asList("1", "5", "7", "3", "9", "11"), mapped4A.toList());
    assertEquals(Arrays.asList("1", "5", "7", "3", "9", "11"), mapped4B.toList());
  }

  public void testTraverseMapStateful() {
    JBTreeTraverser<Integer> t = numTraverser();
    class F extends JBIterable.SFun<Integer, String> {
      int count;

      @Override
      public String fun(Integer o) {
        count++;
        return count + ":" + o;
      }
    }

    JBTreeTraverser<String> mappedA = t.map(new F(), o -> Integer.parseInt(o.substring(o.indexOf(":") + 1)));
    JBTreeTraverser<String> mappedB = t.map(new F());

    assertEquals(Arrays.asList("1:1", "1:2", "1:5", "2:6", "3:7"), mappedA.traverse().take(5).toList()); // FIXME
    assertEquals(Arrays.asList("1:1", "1:2", "1:5", "2:6", "3:7"), mappedA.traverse().take(5).toList()); // FIXME

    assertEquals(Arrays.asList("1:1", "2:2", "3:5", "4:6", "5:7"), mappedB.traverse().take(5).toList());
    assertEquals(Arrays.asList("1:1", "2:2", "3:5", "4:6", "5:7"), mappedB.traverse().take(5).toList());
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
    assertEquals(num2Traverser().withTraversal(TreeTraversal.GUIDED_TRAVERSAL(newGuide(traversal))).toList(),
                 num2Traverser().withTraversal(traversal).toList());
  }


  // FilteredTraverser ----------------------------------------------

  @NotNull
  public JBTreeTraverser<TextRange> rangeTraverser() {
    return new JBTreeTraverser<>(
      r -> r.getLength() < 4 ? JBIterable.empty() : JBIterable.generate(r.getStartOffset(), i -> i += r.getLength() / 4)
        .takeWhile(i -> i < r.getEndOffset())
        .map(i -> TextRange.from(i, r.getLength() / 4)));
  }

  public void testSimpleFilter() {
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), numTraverser().filter(IS_ODD).toList());
  }

  public void testTraverserOpsOrder() {
    int[] count = {0};
    Condition<Integer> c1 = __ -> { assertEquals("incorrect order (1)", 1, count[0]++ % 3 + 1); return true; };
    Condition<Integer> c2 = __ -> { assertEquals("incorrect order (2)", 2, count[0]++ % 3 + 1); return true; };
    Condition<Integer> c3 = __ -> { assertEquals("incorrect order (3)", 3, count[0]++ % 3 + 1); return true; };
    List<Integer> all = numTraverser().toList();

    // expand
    count[0] = 0;
    assertEquals(all, numTraverser().expand(c1).expand(c2).expand(c3).toList());

    // filter
    count[0] = 0;
    assertEquals(all, numTraverser().filter(c1).filter(c2).filter(c3).toList());

    // forceIgnore
    count[0] = 0;
    assertEquals(all, numTraverser().forceIgnore(not(c1)).forceIgnore(not(c2)).forceIgnore(not(c3)).toList());

    // regard
    count[0] = 0;
    assertEquals(all, numTraverser().regard(c1).regard(c2).regard(c3).toList());

    // forceDisregard
    count[0] = 0;
    assertEquals(all, numTraverser().forceDisregard(not(c1)).forceDisregard(not(c2)).forceDisregard(not(c3)).toList());

    // forceDisregard & regard
    count[0] = 0;
    assertEquals(all, numTraverser().regard(c2).forceDisregard(not(c1)).regard(c3).toList());

    // intercept
    count[0] = 0;
    Function.Mono<TreeTraversal> f1 = o -> { c1.value(0); return o; };
    Function.Mono<TreeTraversal> f2 = o -> { c2.value(0); return o; };
    Function.Mono<TreeTraversal> f3 = o -> { c3.value(0); return o; };
    assertEquals(all, numTraverser().intercept(f1).intercept(f2).intercept(f3).toList());
  }

  public void testSimpleExpand() {
    assertEquals(Arrays.asList(1, 2, 3, 8, 9, 10, 4), numTraverser().expand(IS_ODD).toList());
  }

  public void testExpandFilter() {
    assertEquals(Arrays.asList(1, 3, 9), numTraverser().expand(IS_ODD).filter(IS_ODD).toList());
    assertEquals(Arrays.asList(1, 3, 9), numTraverser().expandAndFilter(IS_ODD).toList());
  }

  public void testSkipExpandedDfs() {
    assertEquals(Arrays.asList(2, 8, 9, 10, 4), numTraverser().expand(IS_ODD).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testOnRange() {
    assertEquals(13, numTraverser().onRange(o -> true).traverse().size());
    JBTreeTraverser<TextRange> ranges = rangeTraverser();
    assertEquals(5, ranges.withRoot(TextRange.from(0, 8)).traverse().size());
    assertEquals(Arrays.asList("(0,64)", "(16,32)", "(28,32)", "(29,30)", "(30,31)", "(31,32)", "(32,48)", "(32,36)", "(32,33)", "(33,34)"),
                 ranges.withRoot(TextRange.from(0, 64))
                   .onRange(r -> r.intersects(30, 33))
                   .preOrderDfsTraversal().map(Object::toString).toList());
  }

  public void testRangeChildrenLeavesDfs() {
    assertEquals(Arrays.asList(5, 6, 3, 11, 12, 13),
                 numTraverser().regard(not(inRange(7, 10))).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testRangeChildrenLeavesBfs() {
    assertEquals(Arrays.asList(5, 6, 3, 11, 12, 13),
                 numTraverser().regard(not(inRange(7, 10))).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testHideOneNodeDfs() {
    assertEquals(Arrays.asList(1, 2, 5, 6, 7, 4, 11, 12, 13),
                 numTraverser().expandAndFilter(x -> x != 3).traverse(TreeTraversal.PRE_ORDER_DFS).toList());
  }

  public void testHideOneNodeCompletelyBfs() {
    assertEquals(Arrays.asList(1, 2, 4, 5, 6, 7, 11, 12, 13),
                 numTraverser().expandAndFilter(x -> x != 3).traverse(TreeTraversal.PLAIN_BFS).toList());
  }

  public void testSkipExpandedCompletelyBfs() {
    assertEquals(Arrays.asList(2, 4, 8, 9, 10), numTraverser().expand(IS_ODD).traverse(TreeTraversal.LEAVES_BFS).toList());
  }

  public void testExpandSkipFilterReset() {
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13),
                 numTraverser().expand(IS_ODD).withTraversal(TreeTraversal.LEAVES_DFS).reset().filter(IS_ODD).toList());
  }

  public void testForceExcludeReset() {
    assertEquals(Arrays.asList(1, 2, 6, 4, 12), numTraverser().forceIgnore(IS_ODD).reset().toList());
  }

  public void testForceSkipReset() {
    assertEquals(Arrays.asList(1, 2, 6, 8, 10, 4, 12), numTraverser().forceDisregard(IS_ODD).reset().toList());
  }

  public void testForceSkipLeavesDfs() {
    assertEquals(Arrays.asList(6, 8, 10, 12), numTraverser().forceDisregard(IS_ODD).traverse(TreeTraversal.LEAVES_DFS).toList());
  }

  public void testFilterChildren() {
    assertEquals(Arrays.asList(1, 5, 7, 3, 9, 11, 13), numTraverser().regard(IS_ODD).toList());
  }

  public void testEndlessGraph() {
    JBTreeTraverser<Integer> t = new JBTreeTraverser<>(k -> JBIterable.generate(k, INCREMENT).map(SQUARE).take(3));
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
    JBTreeTraverser<Integer> t = numTraverser();
    JBIterable<Integer> it = t.regard(new FilteredTraverserBase.EdgeFilter<>() {
      @Override
      public boolean value(Integer integer) {
        return (integer / edgeSource) % 2 == 0;
      }
    }).traverse();
    assertEquals(Arrays.asList(1, 2, 5, 8, 10, 4, 11), it.toList());
    assertEquals(Arrays.asList(1, 2, 5, 8, 10, 4, 11), it.toList());
  }

  public void testStatefulChildFilter() {
    JBTreeTraverser<Integer> t = numTraverser();
    class F extends JBIterable.SCond<Integer> {
      int count;
      final boolean value;
      F(boolean initialVal) { value = initialVal; }

      @Override
      public boolean value(Integer integer) {
        return count ++ > 0 == value;
      }
    }

    JBIterable<Integer> it = t.regard(new F(true)).traverse();
    assertEquals(Arrays.asList(1, 5, 6, 7, 3, 9, 10, 4, 12, 13), it.toList());
    assertEquals(Arrays.asList(1, 5, 6, 7, 3, 9, 10, 4, 12, 13), it.toList());
    assertEquals(it.toList(), t.forceDisregard(new F(false)).reset().toList());
  }
}
