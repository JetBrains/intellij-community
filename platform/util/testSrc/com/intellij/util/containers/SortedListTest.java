/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import junit.framework.TestCase;

import java.util.Iterator;
import java.util.ListIterator;

/**
 * @author nik
 */
public class SortedListTest extends TestCase {
  public void testAdd() {
    SortedList<String> list = createList();
    list.add("b");
    list.add("c");
    list.add("a");
    assertEquals(3, list.size());
    assertEquals("a", list.get(0));
    assertEquals("b", list.get(1));
    assertEquals("c", list.get(2));
  }

  public void testRemove() {
    final SortedList<String> list = createList();
    list.add("b");
    list.add("a");
    list.remove("b");
    assertEquals(1, list.size());
    assertEquals("a", list.get(0));
  }

  public void testIterator() {
    final SortedList<String> list = createList();
    list.add("b");
    list.add("a");
    final Iterator<String> iterator = list.iterator();
    assertEquals("a", iterator.next());
    iterator.remove();
    assertEquals("b", iterator.next());
    assertFalse(iterator.hasNext());

    assertEquals(1, list.size());
    assertEquals("b", list.get(0));

    list.add("c");
    assertEquals(2, list.size());
    assertEquals("b", list.get(0));
    assertEquals("c", list.get(1));
  }

  public void testListIterator() {
    final SortedList<String> list = createList();
    list.add("b");
    list.add("c");
    final ListIterator<String> iterator = list.listIterator();
    assertEquals("b", iterator.next());
    assertEquals("c", iterator.next());
    assertEquals("c", iterator.previous());
    assertEquals("b", iterator.previous());
    iterator.add("a");

    assertEquals(3, list.size());
    list.add("d");
    assertEquals(4, list.size());
    assertEquals("a", list.get(0));
    assertEquals("b", list.get(1));
    assertEquals("c", list.get(2));
    assertEquals("d", list.get(3));
  }

  private static SortedList<String> createList() {
    return new SortedList<>(String.CASE_INSENSITIVE_ORDER);
  }
}
