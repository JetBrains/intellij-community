/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.containers.EmptyIterator;
import junit.framework.TestCase;

import java.util.*;

/**
 * @author max
 */
public class SmartListTest extends TestCase {
  public void testEmpty() {
    assertEquals(0, new SmartList<Integer>().size());
  }

  public void testOneElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    assertEquals(1, l.size());
    assertEquals(1, l.get(0).intValue());
  }

  public void testTwoElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    assertEquals(2, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
  }

  public void testThreeElement() {
    List<Integer> l = new SmartList<Integer>();
    l.add(new Integer(1));
    l.add(new Integer(2));
    l.add(new Integer(3));
    assertEquals(3, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
    assertEquals(3, l.get(2).intValue());
  }

  public void testFourElement() {
    SmartList<Integer> l = new SmartList<Integer>();
    int modCount = 0;
    assertEquals(modCount, l.getModificationCount());
    l.add(new Integer(1)); assertEquals(++modCount, l.getModificationCount());
    l.add(new Integer(2)); assertEquals(++modCount, l.getModificationCount());
    l.add(new Integer(3)); assertEquals(++modCount, l.getModificationCount());
    l.add(new Integer(4)); assertEquals(++modCount, l.getModificationCount());
    assertEquals(4, l.size());
    assertEquals(1, l.get(0).intValue());
    assertEquals(2, l.get(1).intValue());
    assertEquals(3, l.get(2).intValue());
    assertEquals(4, l.get(3).intValue());
    assertEquals(modCount, l.getModificationCount());

    l.remove(2);
    assertEquals(3, l.size());
    assertEquals(++modCount, l.getModificationCount());
    assertEquals("[1, 2, 4]", l.toString());

    l.set(2, 3);
    assertEquals(3, l.size());
    assertEquals(modCount, l.getModificationCount());
    assertEquals("[1, 2, 3]", l.toString());

    l.clear();
    assertEquals(0, l.size());
    assertEquals(++modCount, l.getModificationCount());
    assertEquals("[]", l.toString());

    boolean thrown = false;
    try {
      l.set(1, 3);
    }
    catch (IndexOutOfBoundsException e) {
      thrown = true;
    }
    assertTrue("IndexOutOfBoundsException must be thrown", thrown);

    l.clear();
    assertEquals(0, l.size());
    assertEquals(++modCount, l.getModificationCount());
    assertEquals("[]", l.toString());

    Iterator<Integer> iterator = l.iterator();
    assertSame(EmptyIterator.getInstance(), iterator);
    assertFalse(iterator.hasNext());

    l.add(-2);
    iterator = l.iterator();
    assertNotSame(EmptyIterator.getInstance(), iterator);
    assertTrue(iterator.hasNext());
    assertEquals(-2, iterator.next().intValue());
    assertFalse(iterator.hasNext());

    thrown = false;
    try {
      l.get(1);
    }
    catch (IndexOutOfBoundsException e) {
      thrown = true;
    }
    assertTrue("IndexOutOfBoundsException must be thrown", thrown);

    l.addAll(l);
    assertEquals(2, l.size());
    assertEquals("[-2, -2]", l.toString());
    thrown = false;
    try {
      l.addAll(l);
    }
    catch (ConcurrentModificationException e) {
      thrown = true;
    }
    assertTrue("ConcurrentModificationException must be thrown", thrown);
  }

  public void testAddIndexedNegativeIndex() {
    SmartList<Integer> l = new SmartList<Integer>();
    try {
      l.add(-1, new Integer(1));
    }
    catch (Exception e) {
      return;
    }
    fail("IndexOutOfBoundsException must be thrown");
  }

  public void testAddIndexedWrongIndex() {
    SmartList<Integer> l = new SmartList<Integer>(new Integer(1));
    try {
      l.add(3, new Integer(1));
    }
    catch (Exception e) {
      return;
    }
    fail("IndexOutOfBoundsException must be thrown");
  }

  public void testAddIndexedEmptyWrongIndex() {
    SmartList<Integer> l = new SmartList<Integer>();
    try {
      l.add(1, new Integer(1));
    }
    catch (Exception e) {
      return;
    }
    fail("IndexOutOfBoundsException must be thrown");
  }

  public void testAddIndexedEmpty() {
    SmartList<Integer> l = new SmartList<Integer>();
    int modCount = 0;
    l.add(0, new Integer(1)); assertEquals(++modCount, l.getModificationCount());
    assertEquals(1, l.size());
    assertEquals(1, l.get(0).intValue());
  }

  public void testAddIndexedOneElement() {
    SmartList<Integer> l = new SmartList<Integer>(new Integer(0));
    assertEquals(1, l.size());

    int modCount = l.getModificationCount();
    l.add(0, new Integer(42)); assertEquals(++modCount, l.getModificationCount());
    assertEquals(2, l.size());
    assertEquals(42, l.get(0).intValue());
    assertEquals(0, l.get(1).intValue());
  }

  public void testAddIndexedOverOneElement() {
    SmartList<Integer> l = new SmartList<Integer>(new Integer(0));
    assertEquals(1, l.size());

    int modCount = l.getModificationCount();
    l.add(1, new Integer(42)); assertEquals(++modCount, l.getModificationCount());
    assertEquals(2, l.size());
    assertEquals(0, l.get(0).intValue());
    assertEquals(42, l.get(1).intValue());
  }

  public void testAddIndexedOverTwoElements() {
    SmartList<Integer> l = new SmartList<Integer>(Arrays.asList(new Integer[]{new Integer(0), new Integer(1)}));
    assertEquals(2, l.size());

    int modCount = l.getModificationCount();
    l.add(1, new Integer(42)); assertEquals(++modCount, l.getModificationCount());
    assertEquals(3, l.size());
    assertEquals(0, l.get(0).intValue());
    assertEquals(42, l.get(1).intValue());
    assertEquals(1, l.get(2).intValue());
  }

}
