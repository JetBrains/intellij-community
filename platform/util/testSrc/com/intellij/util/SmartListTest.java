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
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.containers.HashSet;
import org.junit.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author max
 */
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

  @Test
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

    boolean thrown = false;
    try {
      l.set(1, 3);
    }
    catch (IndexOutOfBoundsException e) {
      thrown = true;
    }
    assertThat(thrown).as("IndexOutOfBoundsException must be thrown").isTrue();

    l.clear();
    assertThat(l).isEmpty();
    assertThat(l.getModificationCount()).isEqualTo(++modCount);
    assertThat(l.toString()).isEqualTo("[]");

    Iterator<Integer> iterator = l.iterator();
    assertThat(iterator).isSameAs(EmptyIterator.getInstance());
    assertThat(iterator.hasNext()).isFalse();

    l.add(-2);
    iterator = l.iterator();
    assertThat(iterator).isNotSameAs(EmptyIterator.getInstance());
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(-2);
    assertThat(iterator.hasNext()).isFalse();

    thrown = false;
    try {
      l.get(1);
    }
    catch (IndexOutOfBoundsException e) {
      thrown = true;
    }
    assertThat(thrown).as("IndexOutOfBoundsException must be thrown").isTrue();

    l.addAll(l);
    assertThat(l).hasSize(2);
    assertThat(l.toString()).isEqualTo("[-2, -2]");
    thrown = false;
    try {
      l.addAll(l);
    }
    catch (ConcurrentModificationException e) {
      thrown = true;
    }
    assertThat(thrown).as("ConcurrentModificationException must be thrown").isTrue();
  }

  @Test
  public void testAddIndexedNegativeIndex() {
    SmartList<Integer> l = new SmartList<>();
    try {
      l.add(-1, 1);
    }
    catch (Exception e) {
      return;
    }
    fail("IndexOutOfBoundsException must be thrown, " + l);
  }

  @Test
  public void testAddIndexedWrongIndex() {
    SmartList<Integer> l = new SmartList<>(1);
    try {
      l.add(3, 1);
    }
    catch (Exception e) {
      return;
    }
    fail("IndexOutOfBoundsException must be thrown, " + l);
  }

  @Test
  public void testAddIndexedEmptyWrongIndex() {
    SmartList<Integer> l = new SmartList<>();
    try {
      l.add(1, 1);
    }
    catch (Exception e) {
      return;
    }
    fail("IndexOutOfBoundsException must be thrown, " + l);
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

  @Test
  public void testEqualsNonListCollection() {
    List<Integer> list = new SmartList<>();

    assertThat(list).isNotEqualTo(new HashSet<>());
  }

  @Test
  public void testEqualsEmptyList() {
    List<Integer> list = new SmartList<>();

    assertThat(list).isEqualTo(new SmartList<>());
    assertThat(list).isEqualTo(new ArrayList<>());
    assertThat(list).isEqualTo(new LinkedList<>());
    assertThat(list).isEqualTo(Collections.emptyList());
    assertThat(list).isEqualTo(Arrays.asList());
    assertThat(list).isEqualTo(ContainerUtilRt.emptyList());

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
    assertThat(list).isNotEqualTo(ContainerUtilRt.emptyList());
  }

  @Test
  public void testEqualsListWithSingleNonNullElement() {
    List<Integer> list = new SmartList<>(1);

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
    assertThat(list).isNotEqualTo(ContainerUtilRt.emptyList());
  }

  @Test
  public void testEqualsListWithMultipleElements() {
    List<Integer> list = new SmartList<>(1, null, 3);

    assertThat(list).isEqualTo(new SmartList<>(new Integer(1), null, new Integer(3)));
    assertThat(list).isEqualTo(new ArrayList<>(Arrays.asList(new Integer(1), null, new Integer(3))));
    assertThat(list).isEqualTo(new LinkedList<>(Arrays.asList(new Integer(1), null, new Integer(3))));
    assertThat(list).isEqualTo(Arrays.asList(new Integer(1), null, new Integer(3)));

    assertThat(list).isNotEqualTo(new SmartList<>(new Integer(1), new Integer(2), new Integer(3)));
    assertThat(list).isNotEqualTo(new ArrayList<>(Arrays.asList(new Integer(1), new Integer(2), new Integer(3))));
    assertThat(list).isNotEqualTo(new LinkedList<>(Arrays.asList(new Integer(1), new Integer(2), new Integer(3))));
    assertThat(list).isNotEqualTo(Arrays.asList(new Integer(1), new Integer(2), new Integer(3)));
  }
}
