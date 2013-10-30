/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class WeakListTest extends TestCase {
  private final WeakList<Object> myWeakList = new WeakList<Object>();
  protected final List<Object> myHolder = new ArrayList<Object>();

  private void addElement(Object element) {
    myWeakList.add(element);
    myHolder.add(element);
  }

  public void testCompresses() {
    for (int i = 0; i < 20; i++) {
      addElement(new Object());
    }
    assertEquals(20, myWeakList.listSize());
    String obj = "xxx";
    addElement(obj);
    myHolder.clear();
    gc();
    myWeakList.remove(new Object()); // invoke processQueue()
    //obj is held here in the codeblock
    assertEquals(1, myWeakList.listSize());
  }

  private static void gc() {
    ConcurrentMapsTest.tryGcSoftlyReachableObjects();
    WeakReference<Object> weakReference = new WeakReference<Object>(new Object());
    do {
      System.gc();
    }
    while (weakReference.get() != null);
  }

  public void testClear() {
    for (int i = 0; i < 20; i++) {
      addElement(new Object());
    }
    assertEquals(20, myWeakList.listSize());
    myHolder.clear();
    gc();
    myWeakList.clear();
    assertFalse(myWeakList.iterator().hasNext());
  }
  
  public void testIterator() {
    int N = 10;
    for (int i=0; i< N;i++) {
      addElement(i);
    }
    gc();
    Iterator<Integer> iterator = (Iterator)myWeakList.iterator();
    for (int i=0; i< N;i++) {
      assertTrue(iterator.hasNext());
      assertTrue(iterator.hasNext());
      int element = iterator.next();
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

  public void testRemoveAllViaIterator() {
    int N = 10;
    for (int i=0; i< N;i++) {
      addElement(i);
    }
    gc();
    Iterator<Object> iterator = myWeakList.iterator();
    for (int i=0; i< N;i++) {
      assertTrue(iterator.hasNext());
      int element = (Integer)iterator.next();
      assertEquals(i, element);
      iterator.remove();
    }
    assertFalse(iterator.hasNext());
    assertTrue(myWeakList.toStrongList().isEmpty());
  }

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

  public void testIteratorRemoveEmpty() {
    Iterator<Object> iterator = myWeakList.iterator();
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail("must not allow to next");
    }
    catch (NoSuchElementException e) {
    }
    try {
      iterator.remove();
      fail("must not allow to remove");
    }
    catch (NoSuchElementException e) {
    }
  }

  public void testElementGetsGcedInTheMiddleAndListRebuildsItself() {
    int N = 200;
    for (int i = 0; i < N; i++) {
      addElement(new Object());
    }
    String x = new String("xxx");
    addElement(x);
    for (int i = 0; i < N; i++) {
      addElement(new Object());
    }
    gc();
    assertEquals(N + 1 + N, myWeakList.listSize());
    myHolder.clear();
    gc();
    boolean removed = myWeakList.remove("zzz");
    assertFalse(removed);
    assertEquals(1, myWeakList.listSize());
    Object element = myWeakList.iterator().next();
    assertSame(x, element);
  }

  public void testIsEmpty() {
    assertTrue(myWeakList.isEmpty());
    addElement(new Object());
    assertFalse(myWeakList.isEmpty());
    myHolder.clear();
    gc();
    assertEquals(1, myWeakList.listSize());
    assertTrue(myWeakList.isEmpty());
  }
}
