// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class SmartListTest {
  @Test
  public void testEmpty() {
    assertThat(new SmartList<Integer>()).isEmpty();
  }

  @Test
  public void testOneElement() {
    List<Integer> l = new SmartList<>();
    l.add(1);
    assertThat(l).hasSize(1);
    assertThat(l.get(0)).isEqualTo(1);

    assertThat(l.indexOf(1)).isEqualTo(0);
    assertThat(l.indexOf(2)).isEqualTo(-1);
    assertThat(l.contains(1)).isTrue();
    assertThat(l.contains(2)).isFalse();
  }

  @Test
  public void testTwoElement() {
    List<Integer> l = new SmartList<>();
    l.add(1);
    l.add(2);
    assertThat(l).hasSize(2);
    assertThat(l.get(0)).isEqualTo(1);
    assertThat(l.get(1)).isEqualTo(2);

    assertThat(l.indexOf(1)).isEqualTo(0);
    assertThat(l.indexOf(2)).isEqualTo(1);
    assertThat(l.contains(1)).isTrue();
    assertThat(l.contains(2)).isTrue();
    assertThat(l.indexOf(42)).isEqualTo(-1);
    assertThat(l.contains(42)).isFalse();
  }

  @Test
  public void testThreeElement() {
    List<Integer> l = new SmartList<>();
    l.add(1);
    l.add(2);
    l.add(3);
    assertThat(l).hasSize(3);
    assertThat(l.get(0)).isEqualTo(1);
    assertThat(l.get(1)).isEqualTo(2);
    assertThat(l.get(2)).isEqualTo(3);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testFourElement() {
    SmartList<Integer> l = new SmartList<>();
    int modCount = 0;
    assertThat(l.getModificationCount()).isEqualTo(modCount);
    l.add(1);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    l.add(2);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    l.add(3);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    l.add(4);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l).hasSize(4);
    assertThat(l.get(0)).isEqualTo(1);
    assertThat(l.get(1)).isEqualTo(2);
    assertThat(l.get(2)).isEqualTo(3);
    assertThat(l.get(3)).isEqualTo(4);
    assertThat(l.getModificationCount()).isEqualTo(modCount);

    l.remove(2);
    assertThat(l).hasSize(3);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l.toString()).isEqualTo("[1, 2, 4]");

    l.set(2, 3);
    assertThat(l).hasSize(3);
    assertThat(l.getModificationCount()).isEqualTo(modCount);
    assertThat(l.toString()).isEqualTo("[1, 2, 3]");

    l.clear();
    assertThat(l).isEmpty();
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l.toString()).isEqualTo("[]");

    l.set(1, 3);
  }

  @SuppressWarnings("RedundantOperationOnEmptyContainer")
  @Test(expected = IndexOutOfBoundsException.class)
  public void testFourElement2() {
    SmartList<Integer> l = new SmartList<>();
    int modCount = 0;

    l.clear();
    assertThat(l).isEmpty();
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l.toString()).isEqualTo("[]");

    Iterator<Integer> iterator = l.iterator();
    assertThat(iterator).isSameAs(Collections.emptyIterator());
    assertThat(iterator.hasNext()).isFalse();

    l.add(-2);
    iterator = l.iterator();
    assertThat(iterator).isNotSameAs(Collections.emptyIterator());
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(-2);
    assertThat(iterator.hasNext()).isFalse();

    l.get(1);
  }

  @SuppressWarnings({"CollectionAddedToSelf", "RedundantOperationOnEmptyContainer"})
  @Test(expected = ConcurrentModificationException.class)
  public void testFourElement3() {
    SmartList<Integer> l = new SmartList<>();
    l.clear();
    l.add(-2);
    l.addAll(l);
    assertThat(l).hasSize(2);
    assertThat(l.toString()).isEqualTo("[-2, -2]");
    l.addAll(l);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testAddIndexedNegativeIndex() {
    new SmartList<Integer>().add(-1, 1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testAddIndexedWrongIndex() {
    new SmartList<>(1).add(3, 1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void testAddIndexedEmptyWrongIndex() {
    new SmartList<Integer>().add(1, 1);
  }

  @Test
  public void testAddIndexedEmpty() {
    SmartList<Integer> l = new SmartList<>();
    int modCount = 0;
    l.add(0, 1);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l).hasSize(1);
    assertThat(l.get(0)).isEqualTo(1);
  }

  @Test
  public void testAddIndexedOneElement() {
    SmartList<Integer> l = new SmartList<>(0);
    assertThat(l).hasSize(1);

    int modCount = l.getModificationCount();
    l.add(0, 42);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l).hasSize(2);
    assertThat(l.get(0)).isEqualTo(42);
    assertThat(l.get(1)).isEqualTo(0);
  }

  @Test
  public void testAddIndexedOverOneElement() {
    SmartList<Integer> l = new SmartList<>(0);
    assertThat(l).hasSize(1);

    int modCount = l.getModificationCount();
    l.add(1, 42);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l).hasSize(2);
    assertThat(l.get(0)).isEqualTo(0);
    assertThat(l.get(1)).isEqualTo(42);
  }

  @Test
  public void testAddIndexedOverTwoElements() {
    SmartList<Integer> l = new SmartList<>(0, 1);
    assertThat(l).hasSize(2);

    int modCount = l.getModificationCount();
    l.add(1, 42);
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l).hasSize(3);
    assertThat(l.get(0)).isEqualTo(0);
    assertThat(l.get(1)).isEqualTo(42);
    assertThat(l.get(2)).isEqualTo(1);
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @Test
  public void testEmptyToArray() {
    SmartList<Integer> l = new SmartList<>();
    assertThat(new Integer[]{}).isEqualTo(l.toArray());
    assertThat(new Integer[]{}).isEqualTo(l.toArray(new Integer[]{}));
  }

  @Test
  public void testSingleToArray() {
    assertThat(new SmartList<>("foo").toArray(ArrayUtilRt.EMPTY_STRING_ARRAY)).containsExactly("foo");
  }

  @Test
  public void testToArray() {
    SmartList<Integer> l = new SmartList<>(0, 1);
    assertThat(l.toArray()).isEqualTo(new Object[]{0, 1});
    assertThat(l.toArray()).isEqualTo(new Integer[]{0, 1});
    assertThat(l.toArray(new Integer[0])).isEqualTo(new Integer[]{0, 1});

    assertThat(l.toArray(new Integer[4])).containsExactly(0, 1, null, null);

    l.remove(1);

    checkForEach(l);

    assertThat(l.toArray(new Integer[4])).containsExactly(0, null, null, null);
    assertThat(l.toArray()).containsExactly(0);
  }

  @Test
  public void testNullIndexOf() {
    List<Integer> l = new SmartList<>();
    l.add(null);
    l.add(null);

    assertThat(l.indexOf(null)).isEqualTo(0);
    assertThat(l.contains(null)).isTrue();
    assertThat(l.indexOf(42)).isEqualTo(-1);
    assertThat(l.contains(42)).isFalse();
  }

  @Test
  public void testEqualsSelf() {
    List<Integer> list = new SmartList<>();

    assertThat(list).isEqualTo(list);
  }

  @SuppressWarnings({"EqualsBetweenInconvertibleTypes", "MismatchedQueryAndUpdateOfCollection"})
  @Test
  public void testEqualsNonListCollection() {
    List<Integer> list = new SmartList<>();

    assertThat(list.equals(new HashSet<>())).isFalse();
  }

  @Test
  public void testEqualsEmptyList() {
    List<Integer> list = new SmartList<>();

    assertThat(list).isEqualTo(new SmartList<>());
    assertThat(list).isEqualTo(new ArrayList<>());
    assertThat(list).isEqualTo(new LinkedList<>());
    assertThat(list).isEqualTo(Collections.emptyList());
    assertThat(list).isEqualTo(Arrays.asList());
    assertThat(list).isEqualTo(ContainerUtil.emptyList());

    assertThat(list).isNotEqualTo(new SmartList<>(1));
    assertThat(list).isNotEqualTo(new ArrayList<>(Collections.singletonList(1)));
    assertThat(list).isNotEqualTo(new LinkedList<>(Collections.singletonList(1)));
    assertThat(list).isNotEqualTo(Arrays.asList(1));
    assertThat(list).isNotEqualTo(Collections.singletonList(1));
  }

  @Test
  public void testEqualsListWithSingleNullableElement() {
    List<Integer> list = new SmartList<>((Integer)null);

    assertThat(list).isEqualTo(new SmartList<>((Integer)null));
    assertThat(list).isEqualTo(new ArrayList<>(Collections.singletonList(null)));
    assertThat(list).isEqualTo(new LinkedList<>(Collections.singletonList(null)));
    assertThat(list).isEqualTo(Arrays.asList(new Integer[]{null}));
    assertThat(list).isEqualTo(Collections.singletonList(null));

    assertThat(list).isNotEqualTo(new SmartList<>());
    assertThat(list).isNotEqualTo(new ArrayList<>());
    assertThat(list).isNotEqualTo(new LinkedList<>());
    assertThat(list).isNotEqualTo(Collections.emptyList());
    assertThat(list).isNotEqualTo(Arrays.asList());
    assertThat(list).isNotEqualTo(ContainerUtil.emptyList());
  }

  @Test
  public void testEqualsListWithSingleNonNullElement() {
    List<Integer> list = new SmartList<>(1);

    checkForEach(list);

    assertThat(list).isEqualTo(new SmartList<>(new Integer(1)));
    assertThat(list).isEqualTo(new ArrayList<>(Arrays.asList(new Integer(1))));
    assertThat(list).isEqualTo(new LinkedList<>(Arrays.asList(new Integer(1))));
    assertThat(list).isEqualTo(Arrays.asList(new Integer(1)));
    assertThat(list).isEqualTo(Collections.singletonList(new Integer(1)));

    assertThat(list).isNotEqualTo(new SmartList<>());
    assertThat(list).isNotEqualTo(new ArrayList<>());
    assertThat(list).isNotEqualTo(new LinkedList<>());
    assertThat(list).isNotEqualTo(Collections.emptyList());
    assertThat(list).isNotEqualTo(Arrays.asList());
    assertThat(list).isNotEqualTo(ContainerUtil.emptyList());
  }

  @Test
  public void testEqualsListWithMultipleElements() {
    List<Integer> list = new SmartList<>(1, null, 3);

    checkForEach(list);

    assertThat(list).isEqualTo(new SmartList<>(new Integer(1), null, new Integer(3)));
    assertThat(list).isEqualTo(new ArrayList<>(Arrays.asList(new Integer(1), null, new Integer(3))));
    assertThat(list).isEqualTo(new LinkedList<>(Arrays.asList(new Integer(1), null, new Integer(3))));
    assertThat(list).isEqualTo(Arrays.asList(new Integer(1), null, new Integer(3)));

    assertThat(list).isNotEqualTo(new SmartList<>(new Integer(1), new Integer(2), new Integer(3)));
    assertThat(list).isNotEqualTo(new ArrayList<>(Arrays.asList(new Integer(1), new Integer(2), new Integer(3))));
    assertThat(list).isNotEqualTo(new LinkedList<>(Arrays.asList(new Integer(1), new Integer(2), new Integer(3))));
    assertThat(list).isNotEqualTo(Arrays.asList(new Integer(1), new Integer(2), new Integer(3)));
  }

  private static <T> void checkForEach(@NotNull List<T> list) {
    List<T> checkList = new ArrayList<>();
    //noinspection UseBulkOperation
    list.forEach(integer -> checkList.add(integer));
    assertThat(list).isEqualTo(checkList);
  }
}
