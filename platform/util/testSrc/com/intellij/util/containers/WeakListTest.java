/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import junit.framework.Assert;

import java.util.Iterator;

public class WeakListTest extends WeaksTestCase {
  private final WeakReferenceArray<Object> myWeakArray = new WeakReferenceArray<Object>();
  private final WeakList<Object> myWeakList = new WeakList<Object>(myWeakArray);

  protected void setUp() throws Exception {
    super.setUp();
    Assert.assertTrue(WeakReferenceArray.PEFORM_CHECK_THREAD);
    WeakReferenceArray.PEFORM_CHECK_THREAD = false;
  }

  protected void tearDown() throws Exception {
    WeakReferenceArray.PEFORM_CHECK_THREAD = true;
    super.tearDown();
  }

  public void testCompresses() {
    if (!JVM_IS_GC_CAPABLE) return;
    for (int i = 0; i < 20; i++) {
      addElement(new Object(), myWeakArray);
    }
    Assert.assertEquals(20, myWeakList.size());
    checkSameElements(myWeakArray);
    String obj = "xxx";
    myHolder.add(obj);
    myWeakList.add(obj);
    checkSameElements(myWeakArray);
    myHolder.clear();
    gc();
    assertEquals(21, myWeakList.size());
    assertTrue(20 >= myWeakArray.getCorpseCount());
    myWeakList.add(new Object());
    assertTrue(String.valueOf(myWeakList.size()), myWeakList.size() <= 22);
    gc();
    myHolder.add(obj);
    checkSameNotNulls(myWeakArray);
  }
  
  public void testIterator() {
    if (!JVM_IS_GC_CAPABLE) return;
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    gc();
    assertTrue(2 <= myWeakArray.getAliveCount());
    int elementCount = 0;
    for (Object element : myWeakList) {
      assertNotNull(element);
      elementCount++;
    }
    assertEquals(2, elementCount);
  }
  
  public void testRemoveViaIterator() {
    if (!JVM_IS_GC_CAPABLE) return;
    addElement(new Object(), myWeakArray);
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    myWeakArray.add(new Object());
    addElement(new Object(), myWeakArray);
    Iterator<Object> iterator = myWeakList.iterator();
    assertSame(myHolder.get(0), iterator.next());
    while (myHolder.get(1) != iterator.next());
    gc();
    assertTrue(4 <= myWeakArray.getAliveCount());
    iterator.remove();
    gc();
    assertTrue(3 <= myWeakArray.getAliveCount());
    iterator.next();
    gc();
    assertTrue(2 <= myWeakArray.getAliveCount());
    assertSame(myHolder.get(2), iterator.next());
    assertFalse(iterator.hasNext());
    myHolder.remove(1);
    checkSameNotNulls(myWeakArray);
    myWeakArray.compress(-1);
    checkSameElements(myWeakArray);
  }

  public void testRemoveLastViaIterator() {
    if (!JVM_IS_GC_CAPABLE) return;
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    Iterator<Object> iterator = myWeakList.iterator();
    iterator.next();
    assertTrue(iterator.hasNext());
    iterator.next();
    assertFalse(iterator.hasNext());
    iterator.remove();
    assertFalse(iterator.hasNext());
    myHolder.remove(1);
    checkSameNotNulls(myWeakArray);
  }

  public void testIteratorKeepsFirstElement() {
    if (!JVM_IS_GC_CAPABLE) return;
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    Iterator<Object> iterator = myWeakList.iterator();
    assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    assertNotNull(iterator.next());
    assertFalse(iterator.hasNext());
  }
  
  public void testIteratorKeepsNextElement() {
    if (!JVM_IS_GC_CAPABLE) return;
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    addElement(new Object(), myWeakArray);
    Iterator<Object> iterator = myWeakList.iterator();
    iterator.next();
    assertTrue(iterator.hasNext());
    myHolder.clear();
    gc();
    assertNotNull(iterator.next());
    assertFalse(iterator.hasNext());
  }
}
