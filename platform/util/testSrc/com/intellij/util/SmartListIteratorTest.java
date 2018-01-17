// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author stsypanov
 */
@RunWith(value = Parameterized.class)
public class SmartListIteratorTest {

  private String listType;

  public SmartListIteratorTest(String listType) {
    this.listType = listType;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<String> data() {
    return Arrays.asList("ArrayList", "SmartList");
  }

  @Test
  public void testIterateOverAllItems() {
    Integer[] ints = {1, 2};
    Iterator<Integer> iterator = freshList(ints).iterator();

    int itemsCount = 0;
    while (iterator.hasNext()) {
      iterator.next();
      itemsCount++;
    }

    assertThat(itemsCount).isEqualTo(ints.length);

    assertThat(iterator.hasNext()).isFalse();
    try {
      iterator.next();
    } catch (NoSuchElementException e) {
      return;
    }
    fail("NoSuchElementException must be thrown");
  }

  @Test
  public void testCMEThrownWhenRemovingInLoop() {
    Integer[] ints = {1, 2, 3};

    List<Integer> smartList = freshList(ints);

    try {
      for (Integer integer : smartList) {
        smartList.remove(integer);
      }
    } catch (ConcurrentModificationException e) {
      return;
    }
    fail("CME must be thrown");
  }

  @Test
  public void testRemoveAll_checkNSEEThrownCallingNextOnEmptyItr() {
    Integer[] ints = {1, 2, 3};

    Iterator<Integer> iterator = freshList(ints).iterator();

    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }

    assertThat(iterator.hasNext()).isFalse();
    try {
      iterator.next();
    } catch (NoSuchElementException e) {
      return;
    }
    fail("NoSuchElementException must be thrown");
  }

  @Test
  public void testRemoveAll_checkISEThrownCallingRemoveOnEmptyItr() {
    Integer[] ints = {1, 2, 3};

    Iterator<Integer> iterator = freshList(ints).iterator();

    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }

    assertThat(iterator.hasNext()).isFalse();
    try {
      iterator.remove();
    } catch (IllegalStateException e) {
      return;
    }
    fail("NoSuchElementException must be thrown");
  }

  @Test
  public void testRemoveOrder() {
    final Integer first = 1;
    final Integer second = 2;

    Iterator<Integer> iterator = freshList(first, second).iterator();

    iterator.next();
    iterator.remove();

    assertThat(iterator.hasNext()).isTrue();

    Integer next = iterator.next();
    assertThat(next).isEqualTo(second);

    assertThat(iterator.hasNext()).isFalse();
    try {
      iterator.next();
    } catch (NoSuchElementException e) {
      return;
    }
    fail("NoSuchElementException must be thrown");
  }

  @Test
  public void testCMEMustBeThrow_sourceListIsModified() {
    List<Integer> integers = freshList(1, 2);

    Iterator<Integer> iterator = integers.iterator();

    integers.add(3);

    assertThat(iterator.hasNext()).isTrue();

    try {
      iterator.next();
    } catch (ConcurrentModificationException e) {
      return;
    }
    fail("ConcurrentModificationException must be thrown");
  }

  @Test
  public void testCMEMustBeThrow_sourceListIsModifiedAfterNextCalled() {
    List<Integer> integers = freshList(1, 2);

    Iterator<Integer> iterator = integers.iterator();

    iterator.next();

    integers.add(3);

    assertThat(iterator.hasNext()).isTrue();

    try {
      iterator.next();
    } catch (ConcurrentModificationException e) {
      return;
    }
    fail("ConcurrentModificationException must be thrown");
  }

  @Test
  public void testCMEMustBeThrow_sourceListIsModifiedAfterRemoveCalled() {
    List<Integer> integers = freshList(1, 2);

    Iterator<Integer> iterator = integers.iterator();

    iterator.next();
    iterator.remove();

    integers.add(3);

    assertThat(iterator.hasNext()).isTrue();

    try {
      iterator.next();
    } catch (ConcurrentModificationException e) {
      return;
    }
    fail("ConcurrentModificationException must be thrown");
  }

  @Test
  public void testCMEMustBeThrow_removeIndexCalledInLoop() {
    List<Integer> integers = freshList(1, 2);

    Iterator<Integer> iterator = integers.iterator();

    integers.remove(0);

    try {
      while (iterator.hasNext()) {
        iterator.next();
        integers.remove(0);
        iterator.remove();
      }
    } catch (ConcurrentModificationException e) {
      return;
    }
    fail("ConcurrentModificationException must be thrown");
  }

  @Test
  public void testCMEMustBeThrow_clearCalledInLoopAfterCallingNext() {
    List<Integer> integers = freshList(1, 2);

    Iterator<Integer> iterator = integers.iterator();

    try {
      while (iterator.hasNext()) {
        iterator.next();
        integers.clear();
        iterator.remove();
      }
    } catch (ConcurrentModificationException e) {
      return;
    }
    fail("ConcurrentModificationException must be thrown");
  }

  @Test
  public void testCMEMustBeThrow_clearCalledInLoopBeforeCallingNext() {
    List<Integer> integers = freshList(1, 2);

    Iterator<Integer> iterator = integers.iterator();

    try {
      while (iterator.hasNext()) {
        integers.clear();
        iterator.next();
        iterator.remove();
      }
    } catch (ConcurrentModificationException e) {
      return;
    }
    fail("ConcurrentModificationException must be thrown");
  }

  @Test
  public void testCMEMustNotBeThrow_clearCalledInLoopAfterCallingNextAndRemove() {
    List<Integer> integers = freshList(1, 2);

    Iterator<Integer> iterator = integers.iterator();

    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
      integers.clear();
    }
  }

  @NotNull
  private List<Integer> freshList(@NotNull Integer... ints) {
    if ("ArrayList".equals(listType)) {
      return new ArrayList<>(Arrays.asList(ints));
    }
    return new SmartList<>(ints);
  }
}
