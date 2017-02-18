/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.GCUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.*;

public class WeakListTest {
  private static final String HARD_REFERENCED = "xxx";

  private final WeakList<Object> myWeakList = new WeakList<>();
  private final List<Object> myHolder = new ArrayList<>();

  @Test
  public void testCompresses() {
    fillWithObjects(20);
    assertEquals(20, myWeakList.listSize());
    addElement(HARD_REFERENCED);
    assertEquals(21, myWeakList.listSize());
    myHolder.clear();
    while (myWeakList.toStrongList().size() == 21) {
      synchronized (myWeakList) {
        gc();
      }
    }
    synchronized (myWeakList) {
      boolean processed = myWeakList.processQueue();
      assertTrue(myWeakList.toStrongList().toString(), processed); // some refs must be in the queue
    }
    //HARD_REFERENCED is held there
    assertEquals(1, myWeakList.listSize());
    assertSame(HARD_REFERENCED, myWeakList.iterator().next());
  }

  @Test
  public void testClear() {
    fillWithObjects(20);
    assertEquals(20, myWeakList.listSize());
    myHolder.clear();
    gc();
    myWeakList.clear();
    assertFalse(myWeakList.iterator().hasNext());
  }

  @Test
  public void testIterator() {
    int N = 10;
    fillWithInts(N);
    gc();
    Iterator<?> iterator = myWeakList.iterator();
    for (int i = 0; i < N; i++) {
      assertTrue(iterator.hasNext());
      assertTrue(iterator.hasNext());
      int element = (Integer)iterator.next();
      assertEquals(i, element);
    }
    assertFalse(iterator.hasNext());

    int elementCount = 0;
    for (Object element : myWeakList) {
      assertEquals(elementCount, element);
      elementCount++;
    }
    assertEquals(N, elementCount);
  }

  @Test
  public void testRemoveViaIterator() {
    addElement(new Object());
    addElement(new Object());
    addElement(new Object());
    Iterator<Object> iterator = myWeakList.iterator();
    assertSame(myHolder.get(0), iterator.next());
    iterator.remove();
    gc();
    assertEquals(2, myWeakList.toStrongList().size());
    iterator.next();
    gc();
    assertEquals(2, myWeakList.toStrongList().size());
    assertSame(myHolder.get(2), iterator.next());
    assertFalse(iterator.hasNext());
    myHolder.remove(1);
  }

  @Test
  public void testRemoveAllViaIterator() {
    int N = 10;
    fillWithInts(N);
    gc();
    Iterator<Object> iterator = myWeakList.iterator();
    for (int i = 0; i < N; i++) {
      assertTrue(iterator.hasNext());
      int element = (Integer)iterator.next();
      assertEquals(i, element);
      iterator.remove();
    }
    assertFalse(iterator.hasNext());
    assertTrue(myWeakList.toStrongList().isEmpty());
  }

  @Test
  public void testRemoveLastViaIterator() {
    addElement(new Object());
    addElement(new Object());
    Iterator<Object> iterator = myWeakList.iterator();
    iterator.next();
    assertTrue(iterator.hasNext());
    iterator.next();
    assertFalse(iterator.hasNext());
    iterator.remove();
  }

  @Test
  public void testIteratorKeepsFirstElement() {
    addElement(new Object());
    addElement(new Object());
    Iterator<Object> iterator = myWeakList.iterator();
    assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    assertNotNull(iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Test
  public void testIteratorKeepsNextElement() {
    addElement(new Object());
    addElement(new Object());
    addElement(new Object());
    Iterator<Object> iterator = myWeakList.iterator();
    iterator.next();
    assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    assertNotNull(iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Test
  public void testIteratorRemoveEmpty() {
    Iterator<Object> iterator = myWeakList.iterator();
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail("must not allow to next");
    }
    catch (NoSuchElementException ignored) {
    }
    try {
      iterator.remove();
      fail("must not allow to remove");
    }
    catch (NoSuchElementException ignored) {
    }
  }

  @Test
  public void testElementGetsCollectedInTheMiddleAndListRebuildsItself() {
    int N = 200;
    fillWithObjects(N);
    String x = new String("xxx");
    addElement(x);
    fillWithObjects(N);
    gc();
    assertEquals(N + 1 + N, myWeakList.listSize());
    myHolder.clear();
    while (myWeakList.toStrongList().size() == N + 1 + N) {
      synchronized (myWeakList) {
        gc();
      }
    }
    boolean removed = myWeakList.remove("zzz");
    assertFalse(removed);
    assertEquals(1, myWeakList.listSize());
    Object element = myWeakList.iterator().next();
    assertSame(x, element);
  }

  @Test
  public void testIsEmpty() {
    assertTrue(myWeakList.isEmpty());
    addElement(new Object());
    assertFalse(myWeakList.isEmpty());
    myHolder.clear();
    gc();
    assertEquals(1, myWeakList.listSize());
    assertTrue(myWeakList.isEmpty());
  }

  private void addElement(Object element) {
    myWeakList.add(element);
    myHolder.add(element);
  }

  private void fillWithObjects(int n) {
    for (int i = n - 1; i >= 0; i--) {
      addElement(new Object());
    }
  }

  private void fillWithInts(int n) {
    for (int i = 0; i < n; i++) {
      addElement(i);
    }
  }

  private static void gc() {
    GCUtil.tryForceGC();
  }
}
