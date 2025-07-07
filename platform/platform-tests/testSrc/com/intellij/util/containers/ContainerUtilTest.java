// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import junit.framework.TestCase;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.function.ThrowingRunnable;

import java.util.HashSet;
import java.util.HashMap;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

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
      //noinspection ResultOfMethodCallIgnored
      l.get(-1);
      fail();
    }
    catch (IndexOutOfBoundsException ignore) {
    }

    try {
      //noinspection ResultOfMethodCallIgnored
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
      //noinspection ResultOfMethodCallIgnored
      l.get(3);
      fail();
    }
    catch (ConcurrentModificationException ignore) {
    }
  }

  private static Future<?> modifyListUntilStopped(List<Integer> list, AtomicBoolean stopped) {
    return AppExecutorUtil.getAppExecutorService().submit(()-> {
       // simple random generator (may overflow)
       for (int seed = 13; !stopped.get(); seed += 907) {
         // random [0-31] (sign clipped)
         int rand = (seed^(seed>>5)) & 0x1f;

         // grow or shrink the list randomly.
         if (rand < list.size()) {
           list.remove(rand);
         }
         else {
           list.add(seed);
         }
       }
     });
  }

  private static List<Integer> createSequentialList(int size) {
    return ContainerUtil.createLockFreeCopyOnWriteList(IntStream.range(0, size).boxed().toList());
  }

  public void testConcatenatedDynamicListsAreIterableEvenWhenTheyAreChangingDuringIteration() throws Exception {
    List<Integer> list1 = createSequentialList(32);
    List<Integer> list2 = createSequentialList(32);
    List<Integer> concat = ContainerUtil.concat(list1, list2);

    AtomicBoolean stop = new AtomicBoolean(false);
    Future<?> future1 = modifyListUntilStopped(list1, stop);
    Future<?> future2 = modifyListUntilStopped(list2, stop);
    try {
      long count = 0;
      long until = System.currentTimeMillis() + 1000;
      while (System.currentTimeMillis() < until) {
        for (Integer value : concat) {
           count += value;
        }
        // must work on streams (even parallel), too.
        count += concat.parallelStream().count();
      }
    }
    finally {
      stop.set(true); // finally stop even in case of an error
      future1.get();
      future2.get();
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
    List<Integer> actual = ContainerUtil.filter(collection, condition);
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
    final List<Object> my = ContainerUtil.createLockFreeCopyOnWriteList();
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
    List<Object> my = ContainerUtil.createLockFreeCopyOnWriteList();

    for (int i = 0; i < 2; i++) {
      @SuppressWarnings("unchecked")
      Object[] array = ((AtomicReference<Object @NotNull []>)my).get();
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
    Benchmark.newBenchmark("COWList add", () -> {
      for (int it = 0; it < 10; it++) {
        list.clear();
        for (int i = 0; i < count; i++) {
          list.add(ints.get(i));
        }
      }
    }).start();
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
    List<String> my = ContainerUtil.createLockFreeCopyOnWriteList(seq);
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

  public void testLockFreeCOWReplaceAll_Stress() {
    int N = 500 * ForkJoinPool.getCommonPoolParallelism();
    List<Integer> list = ContainerUtil.createLockFreeCopyOnWriteList(IntStream.range(0, N).mapToObj(__->0).toList());
    list.stream().parallel().forEach(__->list.replaceAll(i-> i + 1));
    assertEquals(N*N, list.stream().mapToInt(i -> i).sum());
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
    List<String> expected = Collections.singletonList(value);
    List<String> actual = List.of(value);
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
  public void testCOWRemoveIf() {
    {
      List<String> list = ContainerUtil.createLockFreeCopyOnWriteList(Arrays.asList("a", "b"));
      assertTrue(list.removeIf(e -> e.length() == 1));
      assertReallyEmpty(list);
    }

    {
      List<String> list = ContainerUtil.createLockFreeCopyOnWriteList(Arrays.asList("a", "bb"));
      assertTrue(list.removeIf(e -> e.length() == 1));
      assertEquals("bb", UsefulTestCase.assertOneElement(list));
    }
    {
      List<String> list = ContainerUtil.createLockFreeCopyOnWriteList(Arrays.asList("aa", "b"));
      assertTrue(list.removeIf(e -> e.length() == 1));
      assertEquals("aa", UsefulTestCase.assertOneElement(list));
    }
    {
      List<String> list = ContainerUtil.createLockFreeCopyOnWriteList(Arrays.asList("aa", "bb"));
      assertFalse(list.removeIf(e -> e.length() == 1));
      assertEquals(2, list.size());
    }
  }

  public void testAggregateFunctionsReturnReallyUnmodifiableCollections() {
    ContainerUtil.Options.RETURN_REALLY_UNMODIFIABLE_COLLECTION_FROM_METHODS_MARKED_UNMODIFIABLE = true; // in case the test was started without ApplicationImpl init
    checkUnmodifiable(ContainerUtil.sorted(new ArrayList<>(Arrays.asList(1,2))));
    checkUnmodifiable(ContainerUtil.sorted(new ArrayList<>(Arrays.asList(1, 2)), Comparator.comparingInt(t -> t.hashCode())));
    checkUnmodifiable(ContainerUtil.sorted((Iterable<Integer>)new ArrayList<>(Arrays.asList(1, 2)), Comparator.comparingInt(t -> t.hashCode())));
    checkUnmodifiable(ContainerUtil.append(new ArrayList<>(Arrays.asList(1, 2)), 3));
    checkUnmodifiable(ContainerUtil.append(new ArrayList<>(Arrays.asList(1, 2)), 3,4));
    checkUnmodifiable(ContainerUtil.concat(new ArrayList<>(Arrays.asList(1, 2)), new ArrayList<>(Arrays.asList(1, 2))));
    checkUnmodifiable(ContainerUtil.concat(new ArrayList<>(Arrays.asList(1, 2)), new ArrayList<>(Arrays.asList(1, 2)), new ArrayList<>(Arrays.asList(1, 2))));
    checkUnmodifiable(ContainerUtil.concat(List.of(new ArrayList<>(Arrays.asList(1, 2)), new ArrayList<>(Arrays.asList(1, 2)), new ArrayList<>(Arrays.asList(1, 2)))));
    checkUnmodifiable(ContainerUtil.concat(new String[]{"a","b"}, s->List.of(s,s)));
    checkUnmodifiable(ContainerUtil.filter(new String[]{"a","b"}, s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.filter(new ArrayList<>(Arrays.asList("a","b")), s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.map(new ArrayList<>(Arrays.asList("a","b")), s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.map((Iterable<String>)new ArrayList<>(Arrays.asList("a","b")), s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.map(new ArrayList<>(Arrays.asList("a","b")).iterator(), s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.map(new String[]{"a","b"}, s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.mapNotNull(new ArrayList<>(Arrays.asList("a","b")), s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.mapNotNull((Iterable<String>)new ArrayList<>(Arrays.asList("a","b")), s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.mapNotNull(new String[]{"a","b"}, s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.collect(new ArrayList<>(Arrays.asList("a","b")).iterator()));
    checkUnmodifiable(ContainerUtil.collect(new ArrayList<>(Arrays.asList("a","b")).iterator(), FilteringIterator.InstanceOf.TRUE));
    checkUnmodifiable(ContainerUtil.collect(new ArrayList<>(Arrays.asList("a","b")).iterator(), new FilteringIterator.InstanceOf<>(String.class)));
    checkUnmodifiable(ContainerUtil.map2SetNotNull(new ArrayList<>(Arrays.asList("a","b")), t->t));
    checkUnmodifiable(ContainerUtil.map2Set(new ArrayList<>(Arrays.asList("a","b")), t->t));
    checkUnmodifiable(ContainerUtil.map2Set(new String[]{"a","b"}, t->t));
    checkUnmodifiable(ContainerUtil.notNullize(new HashSet<>(Arrays.asList("a","b"))));
    checkUnmodifiable(ContainerUtil.notNullize(new ArrayList<>(Arrays.asList("a","b"))));
    checkUnmodifiable(ContainerUtil.flatten(new Collection[]{new ArrayList<>(Arrays.asList("a","b")), new ArrayList<>(Arrays.asList("a","b"))}));
    checkUnmodifiable(ContainerUtil.flatten(List.of(new ArrayList<>(Arrays.asList("a","b")), new ArrayList<>(Arrays.asList("a","b")))));
    checkUnmodifiable(ContainerUtil.flatMap(List.of(1,2,3 ), t->List.of(t,t)));
    checkUnmodifiable(ContainerUtil.copyList(new ArrayList<>(Arrays.asList("a","b"))));
    checkUnmodifiable(ContainerUtil.findAll(new String[]{"a","b"}, s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.findAll(new ArrayList<>(Arrays.asList("a","b")), s->!s.isEmpty()));
    checkUnmodifiable(ContainerUtil.findAll(new ArrayList<>(Arrays.asList("a","b")), String.class));
    checkUnmodifiable(ContainerUtil.findAll(new String[]{"a","b"}, String.class));
    checkUnmodifiable(ContainerUtil.prepend(new ArrayList<>(Arrays.asList("a","b")), "c"));
    checkUnmodifiable(ContainerUtil.subArrayAsList(new String[]{"a","b"}, 0,1));
    checkUnmodifiable(ContainerUtil.filterIsInstance(new String[]{"a","b"}, String.class));
    checkUnmodifiable(ContainerUtil.filterIsInstance(new ArrayList<>(Arrays.asList("a","b")), String.class));
    checkUnmodifiable(ContainerUtil.intersection(new ArrayList<>(Arrays.asList("a","b")), new ArrayList<>(Arrays.asList("a","b"))));
    checkUnmodifiable(ContainerUtil.mergeSortedLists(new ArrayList<>(Arrays.asList("a","b")), new ArrayList<>(Arrays.asList("a","b")), String::compareTo, true));
    checkUnmodifiable(ContainerUtil.union(new HashSet<>(Arrays.asList("a","b")), new HashSet<>(Arrays.asList("a","b"))));
    checkUnmodifiable(ContainerUtil.union(new ArrayList<>(Arrays.asList("a","b")), new ArrayList<>(Arrays.asList("a","b"))));
    checkUnmodifiable(ContainerUtil.unmodifiableOrEmptyList(new ArrayList<>(Arrays.asList("a","b"))));
    checkUnmodifiable(ContainerUtil.map2LinkedSet(new ArrayList<>(Arrays.asList("a","b")), t->t));
    checkUnmodifiable(ContainerUtil.packNullables("a","b"));

    checkUnmodifiable(ContainerUtil.unmodifiableOrEmptyMap(new HashMap<>(Map.of("a", "b"))));
    checkUnmodifiable(ContainerUtil.union(new HashMap<>(Map.of("a", "b")), new HashMap<>(Map.of("a", "b"))));
    checkUnmodifiable(ContainerUtil.diff(new HashMap<>(Map.of("a", "b")), new HashMap<>(Map.of("f", "c"))));
    checkUnmodifiable(ContainerUtil.intersection(new HashMap<>(Map.of("a", "b")), new HashMap<>(Map.of("a", "b"))));
    checkUnmodifiable(ContainerUtil.notNullize(new HashMap<>(Map.of("a", "b"))));
    checkUnmodifiable(ContainerUtil.filter(new HashMap<>(Map.of("a", "b")), s->s!=null));
    checkUnmodifiable(ContainerUtil.map2Map(List.of("a", "b"), s-> Pair.create(s, s)));
    checkUnmodifiable(ContainerUtil.map2Map(List.of(Pair.create("a", "b"), Pair.create("c","e"))));
    checkUnmodifiable(ContainerUtil.map2Map(new String[]{"a", "b"}, s-> Pair.create(s, s)));
    checkUnmodifiable(ContainerUtil.map2MapNotNull(List.of("a", "b"), s-> Pair.create(s, s)));
    checkUnmodifiable(ContainerUtil.map2MapNotNull(new String[]{"a", "b"}, s-> Pair.create(s, s)));
    //checkUnmodifiable(ContainerUtil.classify(List.of("a", "b").iterator(), s-> s));
  }

  private <K,V> void checkUnmodifiable(@NotNull Map<K,V> map) {
    assertFalse(map.isEmpty());
    assertThrowsUOE(map, () -> map.clear());
    assertThrowsUOE(map, ()->map.put(null, null));
    assertThrowsUOE(map, ()->map.putIfAbsent(null, null));
    assertThrowsUOE(map, ()->map.putAll(new HashMap<>(map)));
    assertThrowsUOE(map, ()->map.remove(null));
    assertThrowsUOE(map, ()->map.remove(null, null));
    assertThrowsUOE(map, ()->map.computeIfAbsent(null, __->null));
    assertThrowsUOE(map, ()->map.compute(null, (__, ___)->null));
    assertThrowsUOE(map, ()->map.computeIfPresent(null, (__, ___)->null));
    assertThrowsUOE(map, ()->map.computeIfAbsent(null, __->null));
    assertThrowsUOE(map, ()->map.replaceAll((__, ___)->null));
    assertThrowsUOE(map, ()->map.replace(null, null));
    assertThrowsUOE(map, ()->map.replace(null, null, null));
    assertThrowsUOE(map, ()->map.merge(null, null, (__, ___)->null));
    assertThrowsUOE(map, ()->map.merge(null, map.values().iterator().next(), (__, ___)->null));
    assertThrowsUOE(map, ()->map.merge(map.keySet().iterator().next(), map.values().iterator().next(), (__, ___)->null));
    //noinspection RedundantCollectionOperation
    assertThrowsUOE(map, ()-> map.keySet().clear());
    //noinspection RedundantCollectionOperation
    assertThrowsUOE(map, ()-> map.keySet().remove(map.keySet().iterator().next()));
    //noinspection RedundantCollectionOperation
    assertThrowsUOE(map, ()-> map.values().clear());
    assertThrowsUOE(map, ()-> map.values().remove(map.values().iterator().next()));
    assertThrowsUOE(map, ()-> {
      Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
      iterator.next();
      iterator.remove();
    });
    //noinspection RedundantCollectionOperation
    assertThrowsUOE(map, ()-> map.entrySet().clear());
  }

  private static void assertThrowsUOE(Collection<?> collection, ThrowingRunnable runnable) {
    int sizeBefore = collection.size();
    Assert.assertThrows(UnsupportedOperationException.class, runnable);
    assertEquals(sizeBefore, collection.size());
  }
  private static void assertThrowsUOE(Map<?,?> collection, ThrowingRunnable runnable) {
    int sizeBefore = collection.size();
    Assert.assertThrows(UnsupportedOperationException.class, runnable);
    assertEquals(sizeBefore, collection.size());
  }

  private <T> void checkUnmodifiable(@NotNull Collection<T> collection) {
    assertFalse(collection.isEmpty());
    assertThrowsUOE(collection, ()->collection.add(null));
    assertThrowsUOE(collection, ()->collection.add(collection.iterator().next()));
    assertThrowsUOE(collection, ()->collection.addAll(Arrays.asList(null, null)));
    assertThrowsUOE(collection, ()->collection.clear());
    assertThrowsUOE(collection, ()->{
      Iterator<T> iterator = collection.iterator();
      iterator.next();
      iterator.remove();
    });
    assertThrowsUOE(collection, ()->collection.remove(collection.iterator().next()));
    assertThrowsUOE(collection, ()->collection.removeAll(new ArrayList<>(collection)));
    assertThrowsUOE(collection, ()->collection.removeIf(__->true));
    assertThrowsUOE(collection, ()->collection.retainAll(Collections.<T>emptyList()));
    assertThrowsUOE(collection, ()->collection.retainAll(Arrays.<T>asList(null, null)));
    if (collection instanceof List<T> list) {
      assertThrowsUOE(collection, ()->list.add(0, null));
      assertThrowsUOE(collection, ()->list.addAll(0, new ArrayList<>(collection)));
      assertThrowsUOE(collection, ()->{
        ListIterator<T> iterator = list.listIterator();
        iterator.next();
        iterator.remove();
        iterator.add(collection.iterator().next());
        iterator.set(collection.iterator().next());
      });
      assertThrowsUOE(collection, ()->list.sort(null));
      assertThrowsUOE(collection, ()->list.replaceAll(t->t));
      assertThrowsUOE(collection, ()->list.set(0, null));
    }
  }
}